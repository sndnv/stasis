@echo off

echo|set /p="Param1:" 1>&2
set /p param1=
echo Received param1=%param1%

if "%~1"=="fail" (
  echo Command failed
  exit /b 1
)

echo|set /p="Reading Param 2" 1>&2
set /p param2=
echo Received param2=%param2%

echo|set /p="Secret1: " 1>&2
set /p secret1=
echo|set /p="Reading Secret 2: " 1>&2
set /p secret2=

echo Command succeeded with [param1=%param1%,param2=%param2%,secret1=%secret1%,secret2=%secret2%]
