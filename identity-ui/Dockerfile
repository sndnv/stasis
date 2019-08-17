FROM node:12.6-alpine

ENV SERVICE_PATH /opt/identity-ui

RUN mkdir -p ${SERVICE_PATH}
WORKDIR ${SERVICE_PATH}

COPY package.json ./
RUN yarn install

COPY . .
