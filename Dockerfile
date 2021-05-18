# Build

FROM registry.access.redhat.com/ubi8/ubi-minimal:latest

RUN echo -e "[maven]\nname=maven\nstream=3.6\nprofiles=\nstate=enabled\n" >/etc/dnf/modules.d/maven.module
RUN microdnf install maven git-core

RUN git -C "/opt" clone "https://github.com/mizdebsk/java-deptools-native.git"
RUN git -C "/opt" clone "https://github.com/fedora-java/javapackages-validator.git"

RUN cd "/opt/java-deptools-native" && mvn dependency:go-offline
RUN cd "/opt/java-deptools-native" && mvn compile
RUN cd "/opt/java-deptools-native" && mvn install

RUN cd "/opt/javapackages-validator" && mvn dependency:go-offline
RUN cd "/opt/javapackages-validator" && mvn compile
RUN cd "/opt/javapackages-validator" && mvn install

# Runtime

FROM registry.access.redhat.com/ubi8/ubi-minimal:latest

RUN microdnf install java-11-openjdk-headless && microdnf clean all

COPY --from=0 "/opt/javapackages-validator/target/" "/opt/"

ENTRYPOINT ["/usr/bin/java", "-jar", "/opt/assembly-0.1/validator-0.1.jar"]
