# Quarkus Web Bundler

[![Version](https://img.shields.io/maven-central/v/io.quarkiverse.web-bundler/quarkus-web-bundler?logo=apache-maven&style=flat-square)](https://search.maven.org/artifact/io.quarkiverse.web-bundler/quarkus-web-bundler)

Create full-stack web apps and components with this Quarkus extension. It offers zero-configuration bundling and minification (with source-map) for your web app scripts (JS, JSX, TS, TSX), dependencies (jQuery, htmx, Bootstrap, Lit etc.), and styles (CSS, SCSS, SASS).

No need to install NodeJs, it relies on a Java wrapped version of [esbuild](https://esbuild.github.io/) to bundle scripts (js, ts, jsx, tsx) and styles (css, scss, sass) and make them easily available in the templates.

* [x] Production build
* [x] Awesome Dev experience
* [x] Integrated with NPM dependencies through [mvnpm](https://mvnpm.org) or [webjars](https://www.webjars.org/).
* [x] Server Side Web Components (Qute template + Script + Style)

All the information you need to use Quarkus Web Bundler is in the [user documentation](https://docs.quarkiverse.io/quarkus-web-bundler/dev/).


