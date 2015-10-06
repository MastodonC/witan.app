FROM mastodonc/basejava

COPY target/witan-app.jar ~/witan-app.jar
COPY resources/staging.witan-app.edn ~/.witan-app.edn

ENV PORT 3000

EXPOSE 3000

CMD ["java", "-jar", "~/witan-app.jar"]
