#!/usr/bin/env bash
# release.sh — 一键发版脚本
# 用法: ./release.sh <tag> [keystore_path]
# 示例: ./release.sh v1.2 ./release-key.jks
#
# 流程: checkout → build → sign → upload APK + version.json → cleanup
# 必须在项目根目录执行，需要 gh CLI 或 curl + GitHub token

set -euo pipefail

# ── 参数解析 ──────────────────────────────────────────
TAG="${1:?用法: ./release.sh <tag> [keystore_path]}"
KEYSTORE="${2:-./release-key.jks}"

# ── 配置 ─────────────────────────────────────────────
OWNER="Moqim-Flourite"
REPO="recursive_list"
APP_ID="com.moqim.list"

# 签名密钥配置（优先从 local.properties 读取）
if [[ -f local.properties ]]; then
    KS_PASS=$(grep "^keystore.storePassword" local.properties | cut -d= -f2)
    KS_ALIAS=$(grep "^keystore.keyAlias" local.properties | cut -d= -f2)
    KS_KEY_PASS=$(grep "^keystore.keyPassword" local.properties | cut -d= -f2)
else
    KS_PASS="${KS_PASS:-}"
    KS_ALIAS="${KS_ALIAS:-}"
    KS_KEY_PASS="${KS_KEY_PASS:-}"
fi

if [[ -z "$KS_PASS" || -z "$KS_ALIAS" || -z "$KS_KEY_PASS" ]]; then
    echo "❌ 签名配置缺失。请在 local.properties 中配置 keystore.* 或设置环境变量 KS_PASS/KS_ALIAS/KS_KEY_PASS"
    exit 1
fi

# GitHub token（优先从 git remote URL 提取，其次从环境变量）
if [[ -z "${GITHUB_TOKEN:-}" ]]; then
    REMOTE_URL=$(git remote get-url origin 2>/dev/null || true)
    GITHUB_TOKEN=$(echo "$REMOTE_URL" | grep -oP 'github_pat_[^@]+' || true)
fi
if [[ -z "$GITHUB_TOKEN" ]]; then
    echo "❌ GitHub token 未找到。设置 GITHUB_TOKEN 环境变量或确保 git remote URL 中包含 token"
    exit 1
fi

# ── 前置检查 ──────────────────────────────────────────
echo "🔍 检查环境..."

if [[ ! -f "$KEYSTORE" ]]; then
    echo "❌ 签名文件不存在: $KEYSTORE"
    exit 1
fi

# 找 apksigner
APKSIGNER=""
for bt in "$ANDROID_HOME/build-tools"/*/apksigner; do
    [[ -x "$bt" ]] && APKSIGNER="$bt"
done
if [[ -z "$APKSIGNER" ]]; then
    # 尝试常见路径
    for bt in ~/Android/Sdk/build-tools/*/apksigner; do
        [[ -x "$bt" ]] && APKSIGNER="$bt"
    done
fi
if [[ -z "$APKSIGNER" ]]; then
    echo "❌ apksigner 未找到，请设置 ANDROID_HOME"
    exit 1
fi

# 检查 gh CLI（可选，fallback 到 curl）
HAS_GH=false
command -v gh &>/dev/null && HAS_GH=true

# ── 锁定 commit ──────────────────────────────────────
echo "📌 切换到 $TAG ..."
ORIGINAL_BRANCH=$(git branch --show-current)
git checkout "$TAG"

# 从 build.gradle.kts 提取版本信息
VERSION_CODE=$(grep "versionCode" app/build.gradle.kts | grep -oP '\d+')
VERSION_NAME=$(grep "versionName" app/build.gradle.kts | grep -oP '"[^"]+"' | tr -d '"')
echo "📦 版本: $VERSION_NAME (code=$VERSION_CODE)"

# ── 构建 ─────────────────────────────────────────────
echo "🔨 构建 release APK..."
./gradlew assembleRelease

APK_UNSIGNED="app/build/outputs/apk/release/app-release-unsigned.apk"
APK_SIGNED="app/build/outputs/apk/release/app-v${VERSION_NAME}-release.apk"

if [[ ! -f "$APK_UNSIGNED" ]]; then
    echo "❌ 构建失败，APK 不存在"
    git checkout "$ORIGINAL_BRANCH"
    exit 1
fi

# ── 签名 ─────────────────────────────────────────────
echo "🔏 签名 APK..."
cp "$APK_UNSIGNED" "$APK_SIGNED"
"$APKSIGNER" sign \
    --ks "$KEYSTORE" \
    --ks-key-alias "$KS_ALIAS" \
    --ks-pass "pass:$KS_PASS" \
    --key-pass "pass:$KS_KEY_PASS" \
    --v2-signing-enabled true \
    --v3-signing-enabled true \
    "$APK_SIGNED"

# 验证签名
"$APKSIGNER" verify "$APK_SIGNED" >/dev/null 2>&1
echo "✅ 签名验证通过"

# ── 生成 version.json ────────────────────────────────
VERSION_JSON="app/build/outputs/apk/release/version.json"
echo "{\"versionCode\": $VERSION_CODE, \"versionName\": \"$VERSION_NAME\"}" > "$VERSION_JSON"
echo "📄 version.json 已生成"

# ── 上传到 GitHub Release ────────────────────────────
echo "🚀 上传到 GitHub Release $TAG ..."

# 获取 release ID
RELEASE_ID=$(curl -s \
    -H "Authorization: token $GITHUB_TOKEN" \
    "https://api.github.com/repos/$OWNER/$REPO/releases/tags/$TAG" \
    | python3 -c "import json,sys; print(json.load(sys.stdin).get('id',''))" 2>/dev/null)

if [[ -z "$RELEASE_ID" ]]; then
    echo "⚠️  Release $TAG 不存在，创建新 release..."
    if $HAS_GH; then
        gh release create "$TAG" "$APK_SIGNED" "$VERSION_JSON" \
            --repo "$OWNER/$REPO" \
            --title "$TAG - v$VERSION_NAME" \
            --generate-notes
    else
        echo "❌ 需要 gh CLI 来创建 release，或手动在 GitHub 上创建"
        git checkout "$ORIGINAL_BRANCH"
        exit 1
    fi
else
    echo "📎 Release ID: $RELEASE_ID，更新附件..."

    # 删除同名旧附件
    ASSETS=$(curl -s \
        -H "Authorization: token $GITHUB_TOKEN" \
        "https://api.github.com/repos/$OWNER/$REPO/releases/$RELEASE_ID/assets")
    for OLD_NAME in "app-v${VERSION_NAME}-release.apk" "app-release-unsigned.apk" "version.json"; do
        OLD_ID=$(echo "$ASSETS" | python3 -c "
import json,sys
for a in json.load(sys.stdin):
    if a['name'] == '$OLD_NAME': print(a['id']); break
" 2>/dev/null || true)
        if [[ -n "$OLD_ID" ]]; then
            curl -s -X DELETE \
                -H "Authorization: token $GITHUB_TOKEN" \
                "https://api.github.com/repos/$OWNER/$REPO/releases/assets/$OLD_ID" >/dev/null
            echo "  🗑️  已删除旧附件: $OLD_NAME"
        fi
    done

    # 上传新附件
    for FILE in "$APK_SIGNED" "$VERSION_JSON"; do
        FNAME=$(basename "$FILE")
        if [[ "$FNAME" == *.apk ]]; then
            CTYPE="application/vnd.android.package-archive"
        else
            CTYPE="application/json"
        fi
        curl -s -X POST \
            -H "Authorization: token $GITHUB_TOKEN" \
            -H "Content-Type: $CTYPE" \
            --data-binary @"$FILE" \
            "https://uploads.github.com/repos/$OWNER/$REPO/releases/$RELEASE_ID/assets?name=$FNAME" \
            | python3 -c "
import json,sys
d=json.load(sys.stdin)
if 'id' in d: print(f'  ✅ {d[\"name\"]} ({d[\"size\"]} bytes)')
else: print(f'  ❌ 上传失败: {d}')
"
    done
fi

# ── 回到原分支 ───────────────────────────────────────
git checkout "$ORIGINAL_BRANCH"

echo ""
echo "🎉 发版完成！"
echo "   版本: v$VERSION_NAME (code=$VERSION_CODE)"
echo "   Release: https://github.com/$OWNER/$REPO/releases/tag/$TAG"
echo "   APK + version.json 已上传"
