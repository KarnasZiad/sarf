# syntax=docker/dockerfile:1
# Image construite à partir de l'interface déjà compilée, comme le backend à partir de son jar :
#
#   npm ci && npm run build && docker build -t sarf-web:local .
#
# Image non privilégiée : nginx écoute sur le port 8080 et n'a pas besoin d'être root.
FROM nginxinc/nginx-unprivileged:1.29-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY dist /usr/share/nginx/html
EXPOSE 8080
