FROM mastodonc/basejava

COPY target/witan-app.jar /root/witan-app.jar
RUN mkdir /root/.aws
COPY resources/aws-config /root/.aws/config
COPY resources/aws-credentials /root/.aws/credentials

ENV PORT 3000

EXPOSE 3000
EXPOSE 5001

CMD ["java", "-jar", "/root/witan-app.jar"]
