ARG SERVICE_PATH=/opt/stasis-identity-ui

FROM node:12.6-alpine as dev-stage
ARG SERVICE_PATH
WORKDIR ${SERVICE_PATH}
COPY package.json ./
RUN yarn install
COPY . .

FROM dev-stage as build-stage
ARG SERVICE_PATH
WORKDIR ${SERVICE_PATH}
COPY --from=dev-stage ${SERVICE_PATH} ${SERVICE_PATH}
RUN yarn build

FROM nginx:stable-alpine as production-stage
ARG SERVICE_PATH
COPY --from=build-stage ${SERVICE_PATH}/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
