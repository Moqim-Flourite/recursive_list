const themePalette = document.querySelectorAll('.theme-orb');
const widgetStyleTabs = document.querySelectorAll('#widgetStyleTabs .segment-chip');
const defaultHomeTabs = document.querySelectorAll('#defaultHomeTabs .segment-chip');
const followSystemToggle = document.getElementById('followSystemToggle');
const morningViewToggle = document.getElementById('morningViewToggle');
const batteryAction = document.getElementById('batteryAction');
const batteryRow = document.getElementById('batteryRow');
const hyperOsGuideButton = document.getElementById('hyperOsGuideButton');
const hyperOsSheet = document.getElementById('hyperOsSheet');
const sheetBackdrop = document.getElementById('sheetBackdrop');
const sheetCloseButton = document.getElementById('sheetCloseButton');
const desktopGuideButton = document.getElementById('desktopGuideButton');
const desktopGuideSheet = document.getElementById('desktopGuideSheet');
const desktopSheetBackdrop = document.getElementById('desktopSheetBackdrop');
const desktopSheetCloseButton = document.getElementById('desktopSheetCloseButton');

const settingsState = {
  themeChoice: 'light',
  widgetStyle: 'minimal',
  defaultHomeTab: 'today',
  batteryOptimized: false,
  followSystem: true,
  morningViewEnabled: true
};

themePalette.forEach(button => {
  button.addEventListener('click', () => {
    themePalette.forEach(item => item.classList.remove('active'));
    button.classList.add('active');
    settingsState.themeChoice = button.dataset.themeChoice || 'light';
    document.body.dataset.themeChoice = settingsState.themeChoice;
  });
});

widgetStyleTabs.forEach(button => {
  button.addEventListener('click', () => {
    widgetStyleTabs.forEach(item => item.classList.remove('active'));
    button.classList.add('active');
    settingsState.widgetStyle = button.dataset.widgetStyle || 'minimal';
  });
});

defaultHomeTabs.forEach(button => {
  button.addEventListener('click', () => {
    defaultHomeTabs.forEach(item => item.classList.remove('active'));
    button.classList.add('active');
    settingsState.defaultHomeTab = button.dataset.homeTab || 'today';
  });
});

followSystemToggle?.addEventListener('change', () => {
  settingsState.followSystem = !!followSystemToggle.checked;
});

morningViewToggle?.addEventListener('change', () => {
  settingsState.morningViewEnabled = !!morningViewToggle.checked;
});

function renderBatteryState() {
  if (!batteryAction || !batteryRow) return;

  if (settingsState.batteryOptimized) {
    batteryAction.textContent = '✓ 已优化';
    batteryAction.classList.remove('warn');
    batteryAction.classList.add('success');
    batteryRow.classList.add('is-optimized');
  } else {
    batteryAction.textContent = '去配置';
    batteryAction.classList.remove('success');
    batteryAction.classList.add('warn');
    batteryRow.classList.remove('is-optimized');
  }
}

batteryAction?.addEventListener('click', () => {
  settingsState.batteryOptimized = !settingsState.batteryOptimized;
  renderBatteryState();
});

function openSheet(sheet) {
  sheet?.classList.remove('hidden');
}

function closeSheet(sheet) {
  sheet?.classList.add('hidden');
}

hyperOsGuideButton?.addEventListener('click', () => openSheet(hyperOsSheet));
sheetBackdrop?.addEventListener('click', () => closeSheet(hyperOsSheet));
sheetCloseButton?.addEventListener('click', () => closeSheet(hyperOsSheet));

desktopGuideButton?.addEventListener('click', () => openSheet(desktopGuideSheet));
desktopSheetBackdrop?.addEventListener('click', () => closeSheet(desktopGuideSheet));
desktopSheetCloseButton?.addEventListener('click', () => closeSheet(desktopGuideSheet));

renderBatteryState();