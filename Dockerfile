FROM mastodonc/basejava

COPY target/witan-app.jar /root/witan-app.jar
COPY resources/staging.witan-app.edn /root/.witan-app.edn

ENV PORT 3000

EXPOSE 3000

CMD ["java", "-jar", "/root/witan-app.jar"]
