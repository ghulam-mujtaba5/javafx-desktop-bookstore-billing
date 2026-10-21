; Inno Setup Script for Bookstore & Stationery Billing POS
; Version: 1.1.0

#define MyAppName "Bookstore & Stationery Billing POS"
#define MyAppVersion "1.1.0"
#define MyAppPublisher "Ghulam Mujtaba"
#define MyAppURL "https://github.com/ghulam-mujtaba5/javafx-desktop-bookstore-billing"
#define MyAppExeName "BookstoreBilling.exe"

[Setup]
; App Identity & Version Metadata
AppId={{D37F8A2E-4B91-4965-A2FE-9E5C1B855A20}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} v{#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}
VersionInfoVersion={#MyAppVersion}.0
VersionInfoCompany={#MyAppPublisher}
VersionInfoDescription={#MyAppName} Desktop Installer
VersionInfoProductVersion={#MyAppVersion}

; Installation Directories
DefaultDirName={autopf}\BookstoreBilling
DefaultGroupName=Bookstore Billing
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog

; Output Artifacts
OutputDir=target\installer
OutputBaseFilename=BookstoreBilling-Setup-v{#MyAppVersion}
SetupIconFile=icon.ico
UninstallDisplayIcon={app}\{#MyAppExeName}

; Compression & Modern Visuals
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Files]
Source: "target\dist\BookstoreBilling\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\{#MyAppExeName}"
Name: "{group}\{cm:UninstallProgram,{#MyAppName}}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent
