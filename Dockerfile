FROM java:8

COPY /target/dumbster-1.9-SNAPSHOT.jar /dumbster.jar
COPY /target/lib/ /lib/

WORKDIR /

ENTRYPOINT ["java"]

RUN echo "waiting for emails..."

CMD ["-jar", "dumbster.jar"]
