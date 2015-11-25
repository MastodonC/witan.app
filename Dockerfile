FROM mastodonc/basejava

COPY target/witan-app.jar /root/witan-app.jar
COPY resources/staging.witan-app.edn /root/.witan-app.edn
RUN mkdir /root/.aws
COPY resources/aws-config /root/.aws/config
COPY resources/aws-credentials /root/.aws/credentials

ENV PORT 3000

EXPOSE 3000
EXPOSE 5001

CMD ["java", "-jar", "/root/witan-app.jar"]
