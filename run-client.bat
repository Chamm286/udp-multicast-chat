@echo off
chcp 65001 >nul
title MULTICAST CHAT CLIENT

set JDK=C:\Program Files\Eclipse Adoptium\jdk-21.0.6.7-hotspot
set JAVAFX=F:\MulticastChatJavaFX\lib\javafx-sdk-21.0.5\lib
set LIBS=F:\MulticastChatJavaFX\lib\ikonli-core-12.3.1.jar;F:\MulticastChatJavaFX\lib\ikonli-javafx-12.3.1.jar;F:\MulticastChatJavaFX\lib\ikonli-fontawesome5-pack-12.3.1.jar
set PROJ=F:\MulticastChatJavaFX

echo Dang khoi dong CLIENT...
"%JDK%\bin\java" --module-path "%JAVAFX%" --add-modules javafx.controls,javafx.fxml -cp "%PROJ%\bin;%LIBS%" client.ChatClientApp

pause