FROM gitpod/workspace-java-21

USER root

RUN bash -c ". /home/gitpod/.sdkman/bin/sdkman-init.sh \
             && sdk install java 21-graalce \
             && sdk default java 21-graalce"