FROM stasis-client:dev-latest

ARG CLIENT_USER=demiourgos728
ARG CLIENT_PATH=/opt/docker/bin
ARG CLIENT_CONFIG_PATH=/home/${CLIENT_USER}/.config/stasis-client
ARG CLIENT_CERTS_PATH=/home/${CLIENT_USER}/.config/stasis-client/certs

ENV PATH=${PATH}:/home/${CLIENT_USER}/.local/bin/:${CLIENT_PATH}

USER root
RUN apt-get update && apt-get install libffi-dev python3 python3-pip -y
RUN update-alternatives --install /usr/bin/python python /usr/bin/python3 1
RUN update-alternatives --install /usr/bin/pip pip /usr/bin/pip3 1

COPY ./ ${CLIENT_PATH}/
RUN mkdir ${CLIENT_PATH}/logs
RUN touch ${CLIENT_PATH}/logs/stasis-client.log
RUN chown -R ${CLIENT_USER} ${CLIENT_PATH}
RUN chmod +w ${CLIENT_PATH}

RUN mkdir -p ${CLIENT_CONFIG_PATH}
RUN chown -R ${CLIENT_USER} ${CLIENT_CONFIG_PATH}
RUN chmod +w ${CLIENT_CONFIG_PATH}

RUN mkdir -p ${CLIENT_CERTS_PATH}
RUN chown -R ${CLIENT_USER} ${CLIENT_CERTS_PATH}
RUN chmod +w ${CLIENT_CERTS_PATH}

USER ${CLIENT_USER}
WORKDIR ${CLIENT_PATH}
RUN pip install .

ENTRYPOINT ["/bin/sh", "-c"]
CMD ["/bin/sh"]
