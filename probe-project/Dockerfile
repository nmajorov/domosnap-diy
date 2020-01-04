FROM hypriot/rpi-alpine

FROM arm32v6/openjdk:8-jdk-alpine

WORKDIR /opt
COPY target/*.jar ./

ENTRYPOINT ["java", "-jar", "probe-project-1.0-SNAPSHOT.jar", "-Duser.dir=/tmp"]

