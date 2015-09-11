FROM mastodonc/basejava

COPY target/witan.app-0.1.0-SNAPSHOT-standalone.jar witan.app-0.1.0-SNAPSHOT-standalone.jar
COPY resources/prod.witan-app.edn .witan-app.edn

ENV PORT 3000

EXPOSE 3000

CMD ["java", "-jar", "witan.app-0.1.0-SNAPSHOT-standalone.jar"]
