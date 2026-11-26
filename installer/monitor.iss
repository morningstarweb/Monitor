; =====================================================================
; Inno Setup 6 Script for Monitor
; Morning Star Web
; Modern Windows 11 Minimal & Clean Installer Architecture
; =====================================================================

#define MyAppName "Monitor"
#define MyAppVersion "1.0.0"
#define MyAppPublisher "Morning Star Web"
#define MyAppURL "https://morningstarweb.space"
#define MyAppExeName "Monitor.exe"
#define ProjectRoot AddBackslash(SourcePath) + ".."
#define MyAppIcon AddBackslash(SourcePath) + "monitor.ico"
#define MyWizardImage AddBackslash(SourcePath) + "wizard_large.bmp"
#define MyWizardSmallImage AddBackslash(SourcePath) + "wizard_small.bmp"

[Setup]
AppId={{8C41E92A-7B0D-4F3A-9E6B-21A0D8F3E512}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} {#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}
DefaultDirName={localappdata}\Programs\{#MyAppName}
DefaultGroupName={#MyAppName}
AllowNoIcons=yes
OutputDir={#ProjectRoot}\Monitor_App_Package
OutputBaseFilename=Monitor_Setup_v{#MyAppVersion}
SetupIconFile={#MyAppIcon}
WizardImageFile={#MyWizardImage}
WizardSmallImageFile={#MyWizardSmallImage}
WizardStyle=modern
PrivilegesRequired=lowest
CloseApplications=yes
RestartApplications=no
DisableWelcomePage=no
DisableProgramGroupPage=yes
DisableReadyPage=no
UninstallDisplayIcon={app}\{#MyAppExeName},0
UninstallDisplayName={#MyAppName}
Compression=lzma2/ultra64
SolidCompression=yes

; Windows Version & Executable Metadata
VersionInfoVersion={#MyAppVersion}
VersionInfoCompany={#MyAppPublisher}
VersionInfoDescription={#MyAppName} Setup
VersionInfoProductName={#MyAppName}
VersionInfoProductVersion={#MyAppVersion}
VersionInfoCopyright=Copyright (C) 2026 {#MyAppPublisher}

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Messages]
WelcomeLabel1=Welcome to %n{#MyAppName} Setup
WelcomeLabel2=This wizard will install {#MyAppName} on your computer.%n%n{#MyAppName} is a high-performance remote development & system monitor for real-time telemetry, visual Git DAG topology, and private network device pairing.
FinishedHeadingLabel=Setup Completed
FinishedLabel=Setup has finished installing {#MyAppName} on your computer.%n%nThe application may be launched by selecting the installed shortcuts or from the Windows system tray.

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"
Name: "startup"; Description: "Launch {#MyAppName} automatically on Windows startup"; GroupDescription: "Windows Integration:"
Name: "cleaninstall"; Description: "Perform a fresh installation (clear old database and pairing state)"; GroupDescription: "Installation Options:"; Flags: checkedonce

[Files]
Source: "{#ProjectRoot}\bin\{#MyAppExeName}"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#MyAppIcon}"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#SourcePath}\redist\*"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#SourcePath}\tools\*"; DestDir: "{app}\tools"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "{#SourcePath}\EULA.txt"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#SourcePath}\PRIVACY.txt"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#ProjectRoot}\LICENSE"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#ProjectRoot}\THIRD_PARTY_NOTICES.txt"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{autoprograms}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\monitor.ico"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\monitor.ico"; Tasks: desktopicon
Name: "{userstartup}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; WorkingDir: "{app}"; IconFilename: "{app}\monitor.ico"; Tasks: startup

[Registry]
Root: HKCU; Subkey: "Software\Microsoft\Windows\CurrentVersion\Run"; ValueType: string; ValueName: "{#MyAppName}"; ValueData: """{app}\{#MyAppExeName}"""; Flags: uninsdeletevalue; Tasks: startup
Root: HKCU; Subkey: "Software\Microsoft\Windows\CurrentVersion\Explorer\StartupApproved\Run"; ValueType: binary; ValueName: "{#MyAppName}"; ValueData: "02 00 00 00 00 00 00 00 00 00 00 00"; Flags: uninsdeletevalue; Tasks: startup
Root: HKCU; Subkey: "Software\Microsoft\Windows\CurrentVersion\Explorer\StartupApproved\StartupFolder"; ValueType: binary; ValueName: "{#MyAppName}.lnk"; ValueData: "02 00 00 00 00 00 00 00 00 00 00 00"; Flags: uninsdeletevalue; Tasks: startup

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent

[Code]
procedure InitializeWizard;
begin
  // Set clean modern Windows 11 typography (Segoe UI)
  WizardForm.Font.Name := 'Segoe UI';
  
  // Welcome Page Headings & Spacing
  WizardForm.WelcomeLabel1.Font.Name := 'Segoe UI';
  WizardForm.WelcomeLabel1.Font.Size := 14;
  WizardForm.WelcomeLabel1.Font.Style := [fsBold];
  WizardForm.WelcomeLabel2.Font.Name := 'Segoe UI';
  WizardForm.WelcomeLabel2.Font.Size := 9;
  
  // Finished Page Headings & Spacing
  WizardForm.FinishedHeadingLabel.Font.Name := 'Segoe UI';
  WizardForm.FinishedHeadingLabel.Font.Size := 14;
  WizardForm.FinishedHeadingLabel.Font.Style := [fsBold];
  WizardForm.FinishedLabel.Font.Name := 'Segoe UI';
  WizardForm.FinishedLabel.Font.Size := 9;
  
  // Inner Page Titles
  WizardForm.PageNameLabel.Font.Name := 'Segoe UI';
  WizardForm.PageNameLabel.Font.Style := [fsBold];
  WizardForm.PageDescriptionLabel.Font.Name := 'Segoe UI';
end;

function InitializeSetup: Boolean;
var
  ResultCode: Integer;
begin
  Result := True;
  // Automatically terminate any running Monitor background instance so installation proceeds smoothly without file locks
  Exec('taskkill.exe', '/F /IM Monitor.exe /T', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
  Sleep(400);
end;

function PrepareToInstall(var NeedsRestart: Boolean): String;
var
  ResultCode: Integer;
begin
  Result := '';
  // Ensure no lingering processes prior to unpacking
  Exec('taskkill.exe', '/F /IM Monitor.exe /T', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
  Sleep(400);
end;

procedure CurStepChanged(CurStep: TSetupStep);
var
  DataDir: String;
begin
  if (CurStep = ssInstall) and WizardIsTaskSelected('cleaninstall') then
  begin
    DataDir := ExpandConstant('{localappdata}\{#MyAppName}');
    if DirExists(DataDir) then
    begin
      DelTree(DataDir, True, True, True);
    end;
  end;
end;

procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
var
  ResultCode: Integer;
begin
  if CurUninstallStep = usUninstall then
  begin
    Exec('taskkill.exe', '/F /IM Monitor.exe /T', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
    Sleep(400);
  end;
end;
