; Barn Installer Script for Inno Setup
; This script generates a Windows setup.exe installer
; Build: iscc barn.iss (requires Inno Setup 6+)

#define AppName "Barn"
#define AppPublisher "Samson Media"
#define AppURL "https://github.com/samson-media/barn"
#define AppExeName "barn.exe"

; Version is passed from CI: iscc /DAppVersion=0.1.0 barn.iss
#ifndef AppVersion
  #define AppVersion "0.0.0"
#endif

[Setup]
AppId={{7B8A9C5E-4D3F-2A1B-0E9D-8C7B6A5F4E3D}
AppName={#AppName}
AppVersion={#AppVersion}
AppVerName={#AppName} {#AppVersion}
AppPublisher={#AppPublisher}
AppPublisherURL={#AppURL}
AppSupportURL={#AppURL}/issues
DefaultDirName={autopf}\{#AppName}
DefaultGroupName={#AppName}
AllowNoIcons=yes
LicenseFile=..\LICENSE
OutputDir=..\dist
OutputBaseFilename=setup-barn-v{#AppVersion}-windows-x64
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=admin
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
ChangesEnvironment=yes

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "addtopath"; Description: "Add to PATH environment variable"; GroupDescription: "Additional options:"
Name: "installservice"; Description: "Install and start Windows Service"; GroupDescription: "Service options:"; Flags: checkedonce
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
Source: "..\target\barn-windows-x64.exe"; DestDir: "{app}"; DestName: "{#AppExeName}"; Flags: ignoreversion

[Icons]
Name: "{group}\{#AppName} Status"; Filename: "{app}\{#AppExeName}"; Parameters: "status"
Name: "{group}\{#AppName} Service Status"; Filename: "{app}\{#AppExeName}"; Parameters: "service status"
Name: "{group}\Uninstall {#AppName}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\{#AppExeName}"; Tasks: desktopicon

[Registry]
Root: HKLM; Subkey: "SYSTEM\CurrentControlSet\Control\Session Manager\Environment"; \
    ValueType: expandsz; ValueName: "Path"; ValueData: "{olddata};{app}"; \
    Tasks: addtopath; Check: NeedsAddPath('{app}')

[Run]
Filename: "{app}\{#AppExeName}"; Parameters: "service install"; \
    StatusMsg: "Installing Windows Service..."; Tasks: installservice; Flags: runhidden
Filename: "{app}\{#AppExeName}"; Parameters: "service start"; \
    StatusMsg: "Starting Barn service..."; Tasks: installservice; Flags: runhidden

[UninstallRun]
Filename: "{app}\{#AppExeName}"; Parameters: "service stop"; Flags: runhidden; RunOnceId: "StopService"
Filename: "{app}\{#AppExeName}"; Parameters: "service uninstall"; Flags: runhidden; RunOnceId: "UninstallService"

[Code]
function NeedsAddPath(Param: string): boolean;
var
  OrigPath: string;
begin
  if not RegQueryStringValue(HKEY_LOCAL_MACHINE,
    'SYSTEM\CurrentControlSet\Control\Session Manager\Environment',
    'Path', OrigPath)
  then begin
    Result := True;
    exit;
  end;
  Result := Pos(';' + Param + ';', ';' + OrigPath + ';') = 0;
end;

procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
var
  Path: string;
  AppPath: string;
  P: Integer;
begin
  if CurUninstallStep = usPostUninstall then
  begin
    if RegQueryStringValue(HKEY_LOCAL_MACHINE,
      'SYSTEM\CurrentControlSet\Control\Session Manager\Environment',
      'Path', Path) then
    begin
      AppPath := ExpandConstant('{app}');
      P := Pos(';' + AppPath, Path);
      if P > 0 then
      begin
        Delete(Path, P, Length(';' + AppPath));
        RegWriteStringValue(HKEY_LOCAL_MACHINE,
          'SYSTEM\CurrentControlSet\Control\Session Manager\Environment',
          'Path', Path);
      end;
    end;
  end;
end;
