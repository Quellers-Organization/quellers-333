@echo off
SETLOCAL enabledelayedexpansion

TITLE Elasticsearch Service ${project.version}

rem TODO: remove for Elasticsearch 6.x
set bad_env_var=0
if not "%ES_MIN_MEM%" == "" set bad_env_var=1
if not "%ES_MAX_MEM%" == "" set bad_env_var=1
if not "%ES_HEAP_SIZE%" == "" set bad_env_var=1
if not "%ES_HEAP_NEWSIZE%" == "" set bad_env_var=1
if not "%ES_DIRECT_SIZE%" == "" set bad_env_var=1
if not "%ES_USE_IPV4%" == "" set bad_env_var=1
if not "%ES_GC_OPTS%" == "" set bad_env_var=1
if not "%ES_GC_LOG_FILE%" == "" set bad_env_var=1
if %bad_env_var% == 1 (
    echo Error: encountered environment variables that are no longer supported
    echo Use jvm.options or ES_JAVA_OPTS to configure the JVM
    if not "%ES_MIN_MEM%" == "" echo ES_MIN_MEM=%ES_MIN_MEM%: set -Xms%ES_MIN_MEM% in jvm.options or add "-Xms%ES_MIN_MEM%" to ES_JAVA_OPTS
    if not "%ES_MAX_MEM%" == "" echo ES_MAX_MEM=%ES_MAX_MEM%: set -Xms%ES_MAX_MEM% in jvm.options or add "-Xmx%ES_MAX_MEM%" to ES_JAVA_OPTS
    if not "%ES_HEAP_SIZE%" == "" echo ES_HEAP_SIZE=%ES_HEAP_SIZE%: set -Xms%ES_HEAP_SIZE% and -Xmx%ES_HEAP_SIZE% in jvm.options or add "-Xms%ES_HEAP_SIZE% -Xmx%ES_HEAP_SIZE%" to ES_JAVA_OPTS
    if not "%ES_HEAP_NEWSIZE%" == "" echo ES_HEAP_NEWSIZE=%ES_HEAP_NEWSIZE%: set -Xmn%ES_HEAP_NEWSIZE% in jvm.options or add "-Xmn%ES_HEAP_SIZE%" to ES_JAVA_OPTS
    if not "%ES_DIRECT_SIZE%" == "" echo ES_DIRECT_SIZE=%ES_DIRECT_SIZE%: set -XX:MaxDirectMemorySize=%ES_DIRECT_SIZE% in jvm.options or add "-XX:MaxDirectMemorySize=%ES_DIRECT_SIZE%" to ES_JAVA_OPTS
    if not "%ES_USE_IPV4%" == "" echo ES_USE_IPV4=%ES_USE_IPV4%: set -Djava.net.preferIPv4Stack=true in jvm.options or add "-Djava.net.preferIPv4Stack=true" to ES_JAVA_OPTS
    if not "%ES_GC_OPTS%" == "" echo ES_GC_OPTS=%ES_GC_OPTS%: set %ES_GC_OPTS: = and % in jvm.options or add "%ES_GC_OPTS%" to ES_JAVA_OPTS
    if not "%ES_GC_LOG_FILE%" == "" echo ES_GC_LOG_FILE=%ES_GC_LOG_FILE%: set -Xloggc:%ES_GC_LOG_FILE% in jvm.options or add "-Xloggc:%ES_GC_LOG_FILE%" to ES_JAVA_OPTS"
    exit /b 1
)
rem end TODO: remove for Elasticsearch 6.x

if NOT DEFINED JAVA_HOME IF EXIST %ProgramData%\Oracle\java\javapath\java.exe (
    for /f "tokens=2 delims=[]" %%a in ('dir %ProgramData%\Oracle\java\javapath\java.exe') do @set JAVA_EXE=%%a
)
if DEFINED JAVA_EXE set JAVA_HOME=%JAVA_EXE:\bin\java.exe=%
if DEFINED JAVA_EXE (
    ECHO Using JAVA_HOME=%JAVA_HOME% retrieved from %ProgramData%\Oracle\java\javapath\java.exe
)
set JAVA_EXE=
if NOT DEFINED JAVA_HOME goto err

if not "%CONF_FILE%" == "" goto conffileset

set SCRIPT_DIR=%~dp0
for %%I in ("%SCRIPT_DIR%..") do set ES_HOME=%%~dpfI

rem Detect JVM version to figure out appropriate executable to use
if not exist "%JAVA_HOME%\bin\java.exe" (
echo JAVA_HOME points to an invalid Java installation (no java.exe found in "%JAVA_HOME%"^). Exiting...
goto:eof
)

"%JAVA_HOME%\bin\java" -Xmx50M -version > nul 2>&1

if errorlevel 1 (
	echo Warning: Could not start JVM to detect version, defaulting to x86:
	goto x86
)

"%JAVA_HOME%\bin\java" -Xmx50M -version 2>&1 | "%windir%\System32\find" "64-Bit" >nul:

if errorlevel 1 goto x86
set EXECUTABLE=%ES_HOME%\bin\elasticsearch-service-x64.exe
set SERVICE_ID=elasticsearch-service-x64
set ARCH=64-bit
goto checkExe

:x86
set EXECUTABLE=%ES_HOME%\bin\elasticsearch-service-x86.exe
set SERVICE_ID=elasticsearch-service-x86
set ARCH=32-bit

:checkExe
if EXIST "%EXECUTABLE%" goto okExe
echo elasticsearch-service-(x86|x64).exe was not found...

:okExe
set ES_VERSION=${project.version}

if "%LOG_DIR%" == "" set LOG_DIR=%ES_HOME%\logs

if "x%1x" == "xx" goto displayUsage
set SERVICE_CMD=%1
shift
if "x%1x" == "xx" goto checkServiceCmd
set SERVICE_ID=%1

:checkServiceCmd

if "%LOG_OPTS%" == "" set LOG_OPTS=--LogPath "%LOG_DIR%" --LogPrefix "%SERVICE_ID%" --StdError auto --StdOutput auto

if /i %SERVICE_CMD% == install goto doInstall
if /i %SERVICE_CMD% == remove goto doRemove
if /i %SERVICE_CMD% == start goto doStart
if /i %SERVICE_CMD% == stop goto doStop
if /i %SERVICE_CMD% == manager goto doManagment
echo Unknown option "%SERVICE_CMD%"

:displayUsage
echo.
echo Usage: service.bat install^|remove^|start^|stop^|manager [SERVICE_ID]
goto:eof

:doStart
"%EXECUTABLE%" //ES//%SERVICE_ID% %LOG_OPTS%
if not errorlevel 1 goto started
echo Failed starting '%SERVICE_ID%' service
goto:eof
:started
echo The service '%SERVICE_ID%' has been started
goto:eof

:doStop
"%EXECUTABLE%" //SS//%SERVICE_ID% %LOG_OPTS%
if not errorlevel 1 goto stopped
echo Failed stopping '%SERVICE_ID%' service
goto:eof
:stopped
echo The service '%SERVICE_ID%' has been stopped
goto:eof

:doManagment
set EXECUTABLE_MGR=%ES_HOME%\bin\elasticsearch-service-mgr.exe
"%EXECUTABLE_MGR%" //ES//%SERVICE_ID%
if not errorlevel 1 goto managed
echo Failed starting service manager for '%SERVICE_ID%'
goto:eof
:managed
echo Successfully started service manager for '%SERVICE_ID%'.
goto:eof

:doRemove
rem Remove the service
"%EXECUTABLE%" //DS//%SERVICE_ID% %LOG_OPTS%
if not errorlevel 1 goto removed
echo Failed removing '%SERVICE_ID%' service
goto:eof
:removed
echo The service '%SERVICE_ID%' has been removed
goto:eof

:doInstall
echo Installing service      :  "%SERVICE_ID%"
echo Using JAVA_HOME (%ARCH%):  "%JAVA_HOME%"

rem Check JVM server dll first
if exist "%JAVA_HOME%"\jre\bin\server\jvm.dll (
	set JVM_DLL=\jre\bin\server\jvm.dll
	goto foundJVM
)

rem Check 'server' JRE (JRE installed on Windows Server)
if exist "%JAVA_HOME%"\bin\server\jvm.dll (
	set JVM_DLL=\bin\server\jvm.dll
	goto foundJVM
)

rem Fallback to 'client' JRE
if exist "%JAVA_HOME%"\bin\client\jvm.dll (
	set JVM_DLL=\bin\client\jvm.dll
	echo Warning: JAVA_HOME points to a JRE and not JDK installation; a client (not a server^) JVM will be used...
) else (
	echo JAVA_HOME points to an invalid Java installation (no jvm.dll found in "%JAVA_HOME%"^). Exiting...
	goto:eof
)

:foundJVM
if "%ES_JVM_OPTIONS%" == "" (
set ES_JVM_OPTIONS="%ES_HOME%\config\jvm.options"
)

if not "%ES_JAVA_OPTS%" == "" set ES_JAVA_OPTS=%ES_JAVA_OPTS: =;%

@setlocal
for /F "usebackq delims=" %%a in (`findstr /b \- "%ES_JVM_OPTIONS%" ^| findstr /b /v "\-server \-client"`) do set JVM_OPTIONS=!JVM_OPTIONS!%%a;
@endlocal & set ES_JAVA_OPTS=%JVM_OPTIONS%%ES_JAVA_OPTS%

if "%ES_JAVA_OPTS:~-1%"==";" set ES_JAVA_OPTS=%ES_JAVA_OPTS:~0,-1%

@setlocal EnableDelayedExpansion
for %%a in ("%ES_JAVA_OPTS:;=","%") do (
  set var=%%a
  if "!var:~1,4!" == "-Xms" (
    if not "!JVM_MS!" == "" (
      echo duplicate min heap size settings found
      goto:eof
    )
    set XMS=!var:~5,-1!
    call:convertxm !XMS! JVM_MS
  )
  if "!var:~1,16!" == "-XX:MinHeapSize=" (
    if not "!JVM_MS!" == "" (
      echo duplicate min heap size settings found
      goto:eof
    )
    set XMS=!var:~17,-1!
    call:convertxm !XMS! JVM_MS
  )
  if "!var:~1,4!" == "-Xmx" (
    if not "!JVM_MX!" == "" (
      echo duplicate max heap size settings found
      goto:eof
    )
    set XMX=!var:~5,-1!
    call:convertxm !XMX! JVM_MX
  )
  if "!var:~1,16!" == "-XX:MaxHeapSize=" (
    if not "!JVM_MX!" == "" (
      echo duplicate max heap size settings found
      goto:eof
    )
    set XMX=!var:~17,-1!
    call:convertxm !XMX! JVM_MX
  )
  if "!var:~1,4!" == "-Xss" (
    if not "!JVM_SS!" == "" (
      echo duplicate thread stack size settings found
      exit 1
    )
    set XSS=!var:~5,-1!
    call:convertxk !XSS! JVM_SS
  )
  if "!var:~1,20!" == "-XX:ThreadStackSize=" (
    if not "!JVM_SS!" == "" (
      echo duplicate thread stack size settings found
      goto:eof
    )
    set XSS=!var:~21,-1!
    call:convertxk !XSS! JVM_SS
  )
)
@endlocal & set JVM_MS=%JVM_MS% & set JVM_MX=%JVM_MX% & set JVM_SS=%JVM_SS%

if "%JVM_MS%" == "" (
  echo minimum heap size not set; configure via %ES_JVM_OPTIONS% or ES_JAVA_OPTS
  goto:eof
)
if "%JVM_MX%" == "" (
  echo maximum heap size not set; configure via %ES_JVM_OPTIONS% or ES_JAVA_OPTS
  goto:eof
)
if "%JVM_SS%" == "" (
  echo thread stack size not set; configure via %ES_JVM_OPTIONS% or ES_JAVA_OPTS
  goto:eof
)

CALL "%ES_HOME%\bin\elasticsearch.in.bat"
if "%DATA_DIR%" == "" set DATA_DIR=%ES_HOME%\data

if "%CONF_DIR%" == "" set CONF_DIR=%ES_HOME%\config

set ES_PARAMS=-Delasticsearch;-Des.path.home="%ES_HOME%";-Des.default.path.logs="%LOG_DIR%";-Des.default.path.data="%DATA_DIR%";-Des.default.path.conf="%CONF_DIR%"

if "%ES_START_TYPE%" == "" set ES_START_TYPE=manual
if "%ES_STOP_TIMEOUT%" == "" set ES_STOP_TIMEOUT=0

if "%SERVICE_DISPLAY_NAME%" == "" set SERVICE_DISPLAY_NAME=Elasticsearch %ES_VERSION% (%SERVICE_ID%)
if "%SERVICE_DESCRIPTION%" == "" set SERVICE_DESCRIPTION=Elasticsearch %ES_VERSION% Windows Service - https://elastic.co

if not "%SERVICE_USERNAME%" == "" (
	if not "%SERVICE_PASSWORD%" == "" (
		set SERVICE_PARAMS=%SERVICE_PARAMS% --ServiceUser "%SERVICE_USERNAME%" --ServicePassword "%SERVICE_PASSWORD%"
	)
)

"%EXECUTABLE%" //IS//%SERVICE_ID% --Startup %ES_START_TYPE% --StopTimeout %ES_STOP_TIMEOUT% --StartClass org.elasticsearch.bootstrap.Elasticsearch --StopClass org.elasticsearch.bootstrap.Elasticsearch --StartMethod main --StopMethod close --Classpath "%ES_CLASSPATH%" --JvmMs %JVM_MS% --JvmMx %JVM_MX% --JvmSs %JVM_SS% --JvmOptions %ES_JAVA_OPTS% ++JvmOptions %ES_PARAMS% %LOG_OPTS% --PidFile "%SERVICE_ID%.pid" --DisplayName "%SERVICE_DISPLAY_NAME%" --Description "%SERVICE_DESCRIPTION%" --Jvm "%%JAVA_HOME%%%JVM_DLL%" --StartMode jvm --StopMode jvm --StartPath "%ES_HOME%" %SERVICE_PARAMS%

if not errorlevel 1 goto installed
echo Failed installing '%SERVICE_ID%' service
goto:eof

:installed
echo The service '%SERVICE_ID%' has been installed.
goto:eof

:err
echo JAVA_HOME environment variable must be set!
pause
goto:eof

rem ---
rem Function for converting Xm[s|x] values into MB which Commons Daemon accepts
rem ---
:convertxm
set value=%~1
rem extract last char (unit)
set unit=%value:~-1%
rem assume the unit is specified
set conv=%value:~0,-1%

if "%unit%" == "k" goto kilo
if "%unit%" == "K" goto kilo
if "%unit%" == "m" goto mega
if "%unit%" == "M" goto mega
if "%unit%" == "g" goto giga
if "%unit%" == "G" goto giga

rem no unit found, must be bytes; consider the whole value
set conv=%value%
rem convert to KB
set /a conv=%conv% / 1024
:kilo
rem convert to MB
set /a conv=%conv% / 1024
goto mega
:giga
rem convert to MB
set /a conv=%conv% * 1024
:mega
set "%~2=%conv%"
goto:eof

:convertxk
set value=%~1
rem extract last char (unit)
set unit=%value:~-1%
rem assume the unit is specified
set conv=%value:~0,-1%

if "%unit%" == "k" goto kilo
if "%unit%" == "K" goto kilo
if "%unit%" == "m" goto mega
if "%unit%" == "M" goto mega
if "%unit%" == "g" goto giga
if "%unit%" == "G" goto giga

rem no unit found, must be bytes; consider the whole value
set conv=%value%
rem convert to KB
set /a conv=%conv% / 1024
goto kilo
:mega
rem convert to KB
set /a conv=%conv% * 1024
goto kilo
:giga
rem convert to KB
set /a conv=%conv% * 1024 * 1024
:kilo
set "%~2=%conv%"
goto:eof

:conffileset
echo CONF_FILE setting is no longer supported. elasticsearch.yml must be placed in the config directory and cannot be renamed.
goto:eof

ENDLOCAL
