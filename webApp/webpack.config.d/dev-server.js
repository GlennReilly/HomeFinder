// Development only: serve the page on 8081 and forward the API to the Ktor server, so the page and API share one origin
// as they will in production (ADR 0019, spec "Access and security").
config.devServer = Object.assign(config.devServer || {}, {
    port: 8081,
    proxy: [{ context: ["/v1", "/tiles", "/health"], target: "http://localhost:8080" }],
});
