; Inno Setup Script alias for Monitor Desktop Host (Legacy / Secondary to monitor-installer)
; Primary Inno Setup definition lives in ..\installer\monitor.iss.
; WIN-02: single version source (desktop/Cargo.toml workspace version = 1.0.0).
#define MyAppName "Monitor"
#define MyAppVersion "1.0.0"
#define MyAppPublisher "Morning Star Web"
#define MyAppURL "https://morningstarweb.space"
#define MyAppExeName "Monitor.exe"
#define MyAppAssocName MyAppName + " Desktop Host"

[Setup]
AppId={{8C41E92A-7B0D-4F3A-9E6B-21A0D8F3E512}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
DefaultDirName={localappdata}\Programs\{#MyAppName}
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
OutputBaseFilename=Monitor_Setup_v{#MyAppVersion}
OutputDir=target\installer
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
SetupIconFile=crates\monitor-tray\src\monitor.ico
UninstallDisplayIcon={app}\monitor.ico
CloseApplications=yes
RestartApplications=no

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"
Name: "startup"; Description: "Start Monitor automatically when Windows starts"; GroupDescription: "Windows Integration:"

[Files]
Source: "target\release\monitor.exe"; DestDir: "{app}"; DestName: "{#MyAppExeName}"; Flags: ignoreversion
Source: "crates\monitor-tray\src\monitor.ico"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\LICENSE"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\THIRD_PARTY_NOTICES.txt"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\installer\EULA.txt"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\installer\PRIVACY.txt"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\installer\redist\*"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\installer\tools\*"; DestDir: "{app}\tools"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\monitor.ico"
Name: "{group}\{cm:UninstallProgram,{#MyAppName}}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\monitor.ico"; Tasks: desktopicon
Name: "{userstartup}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: startup

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent
