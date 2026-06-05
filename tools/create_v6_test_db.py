#!/usr/bin/env python3
"""
创建 v6 版本的测试数据库，用于验证 MIGRATION_6_7 的孤儿清理逻辑。

数据集（5 种场景）：
  1. 正常数据（月→周→日→任务全链路完整）— 对照组，migration 后应完整保留
  2. weekly_plan 缺失，daily_plan 存在 — 孤儿日计划，应被清理
  3. daily_plan 缺失，execution_task 存在 — 孤儿任务，应被清理
  4. monthly_plan 存在，weekly_plan 全部缺失 — 孤儿周计划路径，应被清理
  5. inbox 任务（weekly_plan_id = NULL）— 应保留，不受清理影响

用法：
  python3 create_v6_test_db.py
  adb push moqim_list_v6_test.db /data/data/com.java.myapplication/databases/moqim_list.db
  # 然后打开 app 触发 migration

验证：
  adb shell "sqlite3 /data/data/com.java.myapplication/databases/moqim_list.db 'SELECT ...'"
"""

import sqlite3
import os
import time

DB_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "moqim_list_v6_test.db")

NOW = int(time.time() * 1000)  # Room 用毫秒时间戳


def create_v6_schema(cur):
    """创建 v6 版本的表结构（无外键约束，有 MIGRATION_5_6 的索引）"""

    # room_master_table（Room 内部用）
    cur.execute("""
        CREATE TABLE IF NOT EXISTS room_master_table (
            id INTEGER PRIMARY KEY,
            identity_hash TEXT
        )
    """)
    cur.execute("""
        INSERT OR REPLACE INTO room_master_table (id, identity_hash)
        VALUES (42, 'placeholder_v6_hash')
    """)

    # monthly_plans
    cur.execute("""
        CREATE TABLE monthly_plans (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            title TEXT NOT NULL,
            theme TEXT NOT NULL DEFAULT '',
            goal TEXT,
            start_date TEXT NOT NULL,
            end_date TEXT NOT NULL,
            status TEXT NOT NULL DEFAULT 'ACTIVE',
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL
        )
    """)

    # weekly_plans（v6 无外键约束）
    cur.execute("""
        CREATE TABLE weekly_plans (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            monthly_plan_id INTEGER NOT NULL,
            title TEXT NOT NULL,
            goal TEXT,
            week_start_date TEXT NOT NULL,
            week_end_date TEXT NOT NULL,
            capacity INTEGER,
            review TEXT,
            status TEXT NOT NULL DEFAULT 'ACTIVE',
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL
        )
    """)
    cur.execute("CREATE INDEX index_weekly_plans_monthly_plan_id ON weekly_plans (monthly_plan_id)")

    # daily_plans（v6 无外键约束）
    cur.execute("""
        CREATE TABLE daily_plans (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            weekly_plan_id INTEGER,
            date TEXT NOT NULL,
            summary TEXT,
            energy_level TEXT,
            generated_from_previous_day INTEGER NOT NULL DEFAULT 0,
            review TEXT,
            status TEXT NOT NULL DEFAULT 'ACTIVE',
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL
        )
    """)
    cur.execute("CREATE INDEX index_daily_plans_weekly_plan_id ON daily_plans (weekly_plan_id)")

    # execution_tasks（v6 无外键约束）
    cur.execute("""
        CREATE TABLE execution_tasks (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            title TEXT NOT NULL,
            note TEXT,
            type TEXT NOT NULL DEFAULT 'PLAN_EXECUTION',
            status TEXT NOT NULL DEFAULT 'TODO',
            priority TEXT NOT NULL DEFAULT 'MEDIUM',
            due_at INTEGER,
            sort_order INTEGER NOT NULL DEFAULT 0,
            monthly_plan_id INTEGER,
            weekly_plan_id INTEGER,
            daily_plan_id INTEGER,
            source_type TEXT NOT NULL DEFAULT 'MANUAL',
            source_task_id INTEGER,
            time_segment TEXT,
            specific_time TEXT,
            is_top_focus INTEGER NOT NULL DEFAULT 0,
            estimated_minutes INTEGER,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL
        )
    """)
    cur.execute("CREATE INDEX index_execution_tasks_daily_plan_id ON execution_tasks (daily_plan_id)")
    cur.execute("CREATE INDEX index_execution_tasks_weekly_plan_id ON execution_tasks (weekly_plan_id)")
    cur.execute("CREATE INDEX index_execution_tasks_monthly_plan_id ON execution_tasks (monthly_plan_id)")
    cur.execute("CREATE INDEX index_execution_tasks_status ON execution_tasks (status)")
    cur.execute("CREATE INDEX index_execution_tasks_time_segment ON execution_tasks (time_segment)")

    # habit_templates
    cur.execute("""
        CREATE TABLE habit_templates (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            title TEXT NOT NULL,
            note TEXT,
            estimated_minutes INTEGER,
            sort_order INTEGER NOT NULL DEFAULT 0,
            enabled INTEGER NOT NULL DEFAULT 1,
            frequency_type TEXT NOT NULL DEFAULT 'DAILY',
            weekdays_mask INTEGER,
            preferred_time_segment TEXT,
            target_app_package_name TEXT,
            icon_uri TEXT,
            icon_label TEXT,
            daily_target_count INTEGER NOT NULL DEFAULT 1,
            streak_enabled INTEGER NOT NULL DEFAULT 1,
            base_completed_days INTEGER NOT NULL DEFAULT 0,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL
        )
    """)

    # habit_records
    cur.execute("""
        CREATE TABLE habit_records (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            habit_template_id INTEGER NOT NULL,
            date TEXT NOT NULL,
            status TEXT NOT NULL DEFAULT 'TODO',
            completed_at INTEGER,
            note TEXT,
            completed_count INTEGER NOT NULL DEFAULT 0
        )
    """)
    cur.execute("CREATE UNIQUE INDEX index_habit_records_habit_template_id_date ON habit_records (habit_template_id, date)")

    # surface_configs
    cur.execute("""
        CREATE TABLE surface_configs (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            target_type TEXT NOT NULL,
            target_id INTEGER,
            surface_type TEXT NOT NULL,
            theme TEXT NOT NULL DEFAULT 'DEFAULT',
            max_items INTEGER NOT NULL DEFAULT 5,
            show_completed INTEGER NOT NULL DEFAULT 0,
            show_progress INTEGER NOT NULL DEFAULT 1,
            opacity REAL NOT NULL DEFAULT 1.0,
            anchor_position TEXT NOT NULL DEFAULT 'TOP',
            text_scale REAL NOT NULL DEFAULT 1.0,
            refresh_policy TEXT NOT NULL DEFAULT 'AUTO'
        )
    """)

    # widget_instance_configs
    cur.execute("""
        CREATE TABLE widget_instance_configs (
            app_widget_id INTEGER PRIMARY KEY NOT NULL,
            surface_config_id INTEGER NOT NULL
        )
    """)


def insert_test_data(cur):
    """插入 5 种测试数据集"""

    # ============================================================
    # 场景 1：正常数据（全链路完整）— 对照组
    # ============================================================
    # 月计划 id=1
    cur.execute("""
        INSERT INTO monthly_plans (id, title, theme, goal, start_date, end_date, status, created_at, updated_at)
        VALUES (1, '6月计划-对照组', '工作', '完成核心功能开发', '2026-06-01', '2026-06-30', 'ACTIVE', ?, ?)
    """, (NOW, NOW))

    # 周计划 id=1（属于月计划 1）
    cur.execute("""
        INSERT INTO weekly_plans (id, monthly_plan_id, title, goal, week_start_date, week_end_date, status, created_at, updated_at)
        VALUES (1, 1, '第1周-对照组', '搭建基础框架', '2026-06-01', '2026-06-07', 'ACTIVE', ?, ?)
    """, (NOW, NOW))

    # 周计划 id=2（属于月计划 1）
    cur.execute("""
        INSERT INTO weekly_plans (id, monthly_plan_id, title, goal, week_start_date, week_end_date, status, created_at, updated_at)
        VALUES (2, 1, '第2周-对照组', '实现核心逻辑', '2026-06-08', '2026-06-14', 'ACTIVE', ?, ?)
    """, (NOW, NOW))

    # 日计划 id=1（属于周计划 1）
    cur.execute("""
        INSERT INTO daily_plans (id, weekly_plan_id, date, summary, energy_level, status, created_at, updated_at)
        VALUES (1, 1, '2026-06-01', '搭建项目骨架', 'HIGH', 'ACTIVE', ?, ?)
    """, (NOW, NOW))

    # 日计划 id=2（属于周计划 1）
    cur.execute("""
        INSERT INTO daily_plans (id, weekly_plan_id, date, summary, energy_level, status, created_at, updated_at)
        VALUES (2, 1, '2026-06-02', '编写数据层', 'MEDIUM', 'ACTIVE', ?, ?)
    """, (NOW, NOW))

    # 日计划 id=3（属于周计划 2）
    cur.execute("""
        INSERT INTO daily_plans (id, weekly_plan_id, date, summary, energy_level, status, created_at, updated_at)
        VALUES (3, 2, '2026-06-08', '实现核心算法', 'HIGH', 'ACTIVE', ?, ?)
    """, (NOW, NOW))

    # 任务 id=1~3（属于日计划 1，全链路完整）
    for i, (title, seg) in enumerate([
        ("初始化 Room 数据库", "MORNING"),
        ("创建 Entity 定义", "AFTERNOON"),
        ("编写 DAO 接口", "EVENING"),
    ], start=1):
        cur.execute("""
            INSERT INTO execution_tasks (id, title, status, monthly_plan_id, weekly_plan_id, daily_plan_id, time_segment, created_at, updated_at)
            VALUES (?, ?, 'TODO', 1, 1, 1, ?, ?, ?)
        """, (i, title, seg, NOW, NOW))

    # 任务 id=4（属于日计划 2）
    cur.execute("""
        INSERT INTO execution_tasks (id, title, status, monthly_plan_id, weekly_plan_id, daily_plan_id, time_segment, created_at, updated_at)
        VALUES (4, '编写 Repository 层', 'TODO', 1, 1, 2, 'MORNING', ?, ?)
    """, (NOW, NOW))

    # 任务 id=5（属于日计划 3）
    cur.execute("""
        INSERT INTO execution_tasks (id, title, status, monthly_plan_id, weekly_plan_id, daily_plan_id, time_segment, created_at, updated_at)
        VALUES (5, '实现级联删除', 'TODO', 1, 2, 3, 'AFTERNOON', ?, ?)
    """, (NOW, NOW))

    # ============================================================
    # 场景 2：weekly_plan 缺失，daily_plan 存在（孤儿日计划）
    # daily_plan.weekly_plan_id 指向不存在的 weekly_plan id=99
    # ============================================================
    cur.execute("""
        INSERT INTO daily_plans (id, weekly_plan_id, date, summary, status, created_at, updated_at)
        VALUES (10, 99, '2026-06-15', '孤儿日计划-周计划已不存在', 'ACTIVE', ?, ?)
    """, (NOW, NOW))

    # 任务 id=10（属于孤儿日计划 10）
    cur.execute("""
        INSERT INTO execution_tasks (id, title, status, daily_plan_id, created_at, updated_at)
        VALUES (10, '孤儿任务-跟着孤儿日计划', 'TODO', 10, ?, ?)
    """, (NOW, NOW))

    # ============================================================
    # 场景 3：daily_plan 缺失，execution_task 存在（孤儿任务）
    # execution_task.daily_plan_id 指向不存在的 daily_plan id=88
    # ============================================================
    cur.execute("""
        INSERT INTO execution_tasks (id, title, status, daily_plan_id, created_at, updated_at)
        VALUES (20, '孤儿任务-日计划已不存在', 'TODO', 88, ?, ?)
    """, (NOW, NOW))

    # ============================================================
    # 场景 4：monthly_plan 存在，weekly_plan 全部缺失
    # 月计划 id=2 存在，但它下面没有任何周计划
    # 这个场景测试的是：月计划本身不会被删（它不是孤儿），但如果有 daily_plan
    # 指向不存在的 weekly_plan 会被清理
    # ============================================================
    cur.execute("""
        INSERT INTO monthly_plans (id, title, theme, goal, start_date, end_date, status, created_at, updated_at)
        VALUES (2, '7月计划-空壳', '学习', '暂无安排', '2026-07-01', '2026-07-31', 'ACTIVE', ?, ?)
    """, (NOW, NOW))

    # 这个月计划下面有一个周计划指向不存在的月计划 id=999（混合孤儿）
    # 实际上这属于场景 2 的变体，但月计划 2 本身是完整的
    cur.execute("""
        INSERT INTO weekly_plans (id, monthly_plan_id, title, week_start_date, week_end_date, status, created_at, updated_at)
        VALUES (100, 999, '孤儿周计划-月计划999不存在', '2026-07-01', '2026-07-07', 'ACTIVE', ?, ?)
    """, (NOW, NOW))

    # 属于这个孤儿周计划的日常计划
    cur.execute("""
        INSERT INTO daily_plans (id, weekly_plan_id, date, summary, status, created_at, updated_at)
        VALUES (21, 100, '2026-07-01', '跟着孤儿周计划的日常', 'ACTIVE', ?, ?)
    """, (NOW, NOW))

    # 属于这个孤儿日常计划的任务
    cur.execute("""
        INSERT INTO execution_tasks (id, title, status, daily_plan_id, weekly_plan_id, created_at, updated_at)
        VALUES (21, '孤儿链任务-月→周→日全断', 'TODO', 21, 100, ?, ?)
    """, (NOW, NOW))

    # ============================================================
    # 场景 5：inbox 任务（weekly_plan_id = NULL, daily_plan_id = NULL）
    # 这些任务不属于任何计划层级，migration 清理时必须跳过
    # ============================================================
    for i, title in enumerate([
        "inbox: 买新键盘",
        "inbox: 整理笔记",
        "inbox: 回复邮件",
    ], start=30):
        cur.execute("""
            INSERT INTO execution_tasks (id, title, status, weekly_plan_id, daily_plan_id, created_at, updated_at)
            VALUES (?, ?, 'TODO', NULL, NULL, ?, ?)
        """, (i, title, NOW, NOW))

    # inbox 任务变体：monthly_plan_id 也不为 NULL 但 daily/weekly 为 NULL
    # 模拟"只挂了月计划但没有分配到具体日"的任务
    cur.execute("""
        INSERT INTO execution_tasks (id, title, status, monthly_plan_id, weekly_plan_id, daily_plan_id, created_at, updated_at)
        VALUES (33, 'inbox变体: 挂月计划但无日分配', 'TODO', 1, NULL, NULL, ?, ?)
    """, (NOW, NOW))


def print_summary(cur):
    """打印数据摘要，方便验证"""
    print("\n" + "=" * 60)
    print("v6 测试数据库创建完成")
    print("=" * 60)

    tables = [
        ("monthly_plans", "月计划"),
        ("weekly_plans", "周计划"),
        ("daily_plans", "日计划"),
        ("execution_tasks", "执行任务"),
    ]

    for table, label in tables:
        count = cur.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0]
        print(f"  {label} ({table}): {count} 条")

    print("\n数据集分布：")
    print("  [场景1] 对照组 - 月1→周1,2→日1,2,3→任务1~5（全链路完整）")
    print("  [场景2] 孤儿日计划 - 日10(周99不存在)，任务10")
    print("  [场景3] 孤儿任务 - 任务20(日88不存在)")
    print("  [场景4] 混合孤儿 - 月2(空壳) + 周100(月999不存在)→日21→任务21")
    print("  [场景5] inbox任务 - 任务30,31,32(NULL) + 任务33(仅挂月计划)")

    print("\nMigration 后预期：")
    print("  ✓ 保留：月1→周1,2→日1,2,3→任务1~5 + 月2(空壳) + 任务30~33(inbox)")
    print("  ✗ 清理：日10 + 任务10 + 任务20 + 周100 + 日21 + 任务21")

    print(f"\n数据库路径: {DB_PATH}")
    print("\n推送到设备：")
    print(f"  adb push {DB_PATH} /data/data/com.java.myapplication/databases/moqim_list.db")
    print("  # 如果 app 正在运行，先 force-stop")
    print("  adb shell am force-stop com.java.myapplication")
    print("  # 然后打开 app 触发 migration")

    print("\n验证命令：")
    print('  adb shell "sqlite3 /data/data/com.java.myapplication/databases/moqim_list.db \\')
    print("    'SELECT id, title FROM monthly_plans ORDER BY id'\\" + '"')
    print('  adb shell "sqlite3 /data/data/com.java.myapplication/databases/moqim_list.db \\')
    print("    'SELECT id, monthly_plan_id, title FROM weekly_plans ORDER BY id'\\" + '"')
    print('  adb shell "sqlite3 /data/data/com.java.myapplication/databases/moqim_list.db \\')
    print("    'SELECT id, weekly_plan_id, date FROM daily_plans ORDER BY id'\\" + '"')
    print('  adb shell "sqlite3 /data/data/com.java.myapplication/databases/moqim_list.db \\')
    print("    'SELECT id, daily_plan_id, weekly_plan_id, title FROM execution_tasks ORDER BY id'\\" + '"')


def main():
    if os.path.exists(DB_PATH):
        os.remove(DB_PATH)
        print(f"已删除旧文件: {DB_PATH}")

    conn = sqlite3.connect(DB_PATH)
    cur = conn.cursor()

    # 关闭外键约束（v6 没有外键，migration 时才加）
    cur.execute("PRAGMA foreign_keys = OFF")

    create_v6_schema(cur)
    insert_test_data(cur)
    conn.commit()

    print_summary(cur)
    conn.close()


if __name__ == "__main__":
    main()
