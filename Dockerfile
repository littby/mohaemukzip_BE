FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# JAR 파일 메인 디렉토리에 복사
COPY build/libs/app.jar app.jar
RUN ln -sf /usr/share/zoneinfo/Asia/Seoul /etc/localtime && echo "Asia/Seoul" > /etc/timezone

ENV SPRING_PROFILES_ACTIVE=prod

# 시스템 진입점 정의
ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "-jar","app.jar", "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE}"]
