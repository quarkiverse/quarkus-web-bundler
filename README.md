# Quarkus Web Bundler
<!-- ALL-CONTRIBUTORS-BADGE:START - Do not remove or modify this section -->
[![All Contributors](https://img.shields.io/badge/all_contributors-1-orange.svg?style=flat-square)](#contributors-)
<!-- ALL-CONTRIBUTORS-BADGE:END -->

[![Build](https://github.com/quarkiverse/quarkus-web-bundler/actions/workflows/build.yml/badge.svg)](https://github.com/quarkiverse/quarkus-web-bundler/actions/workflows/build.yml) 
[![Issues](https://img.shields.io/github/issues/quarkiverse/quarkus-web-bundler)](https://github.com/quarkiverse/quarkus-web-bundler/issues) 
[![Maven Central](https://img.shields.io/maven-central/v/io.quarkiverse.web-bundler/quarkus-web-bundler?logo=apache-maven&style=flat-square)](https://search.maven.org/artifact/io.quarkiverse.web-bundler/quarkus-web-bundler)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

<!-- ALL-CONTRIBUTORS-BADGE:START - Do not remove or modify this section -->
[![All Contributors](https://img.shields.io/badge/all_contributors-1-orange.svg?style=flat-square)](#contributors-)
<!-- ALL-CONTRIBUTORS-BADGE:END -->

Create full-stack web apps and components with this Quarkus extension. It offers zero-configuration bundling and minification (with source-map) for your web app scripts (JS, JSX, TS, TSX), dependencies (jQuery, htmx, Bootstrap, Lit etc.), and styles (CSS, SCSS, SASS).

* [x] Production build
* [x] Awesome Dev experience
* [x] Integrated with NPM dependencies through [mvnpm](https://docs.quarkiverse.io/quarkus-web-bundler/dev/advanced-guides.html#mvnpm) or [webjars](https://docs.quarkiverse.io/quarkus-web-bundler/dev/advanced-guides.html#webjars).
* [x] Build-time index.html rendering with bundled scripts and styles
* [x] Server Side Qute Components (Qute template + Script + Style)

**Is it the same as Quinoa?** It is very close but:

- It is already integrated with a bundler (esbuild, which is very similar to Webpack or Rollup)
- NodeJS is not needed
- It works without any configuration
- All the npm catalog is available directly as dependencies in your pom.xml/build.gradle

**When should I use Quinoa instead?**

- I have a dedicated UI team very familliar with the NodeJS eco-system
- I need a very specific NodeJs/Bundling configuration that the web-bundler does not cover

**How to test my Web App without Jest or similar?**

Use `@QuarkusTest` with [Quarkus Playwright](https://docs.quarkiverse.io/quarkus-playwright/dev/).
It is very easy to create full-stack tests to cover all your scenarios (and re-using all the backend testing data).

**Can I swith from Quinoa to the Web Bundler?**

Yes, just move your web-dependencies to the pom.xml/build.gradle and follow the web-bundler structure, the output app should be the same.

**Can I swith from the Web Bundler to Quinoa?**

Yes, you just need to use a framework (like Vite) and switch to a package.json and follow your framework structure, the output app should be the same.



All the information you need to use Quarkus Web Bundler is in the [user documentation](https://docs.quarkiverse.io/quarkus-web-bundler/dev/).

## Contributors ✨

Thanks goes to these wonderful people ([emoji key](https://allcontributors.org/docs/en/emoji-key)):
<!-- ALL-CONTRIBUTORS-LIST:START - Do not remove or modify this section -->
<!-- prettier-ignore-start -->
<!-- markdownlint-disable -->
<table>
  <tbody>
    <tr>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/blazmrak"><img src="https://avatars.githubusercontent.com/u/25981532?v=4?s=100" width="100px;" alt="blazmrak"/><br /><sub><b>blazmrak</b></sub></a><br /><a href="#mentoring-blazmrak" title="Mentoring">🧑‍🏫</a></td>
    </tr>
  </tbody>
</table>

<!-- markdownlint-restore -->
<!-- prettier-ignore-end -->

<!-- ALL-CONTRIBUTORS-LIST:END -->

<!-- ALL-CONTRIBUTORS-LIST:START - Do not remove or modify this section -->
<!-- prettier-ignore-start -->
<!-- markdownlint-disable -->
<table>
  <tbody>
    <tr>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/ia3andy"><img src="https://avatars.githubusercontent.com/u/2223984?v=4?s=100" width="100px;" alt="Andy Damevin"/><br /><sub><b>Andy Damevin</b></sub></a><br /><a href="https://github.com/quarkiverse/quarkus-quinoa/commits?author=ia3andy" title="Code">💻</a> <a href="#maintenance-ia3andy" title="Maintenance">🚧</a></td>
    </tr>
  </tbody>
</table>

<!-- markdownlint-restore -->
<!-- prettier-ignore-end -->

<!-- ALL-CONTRIBUTORS-LIST:END -->

This project follows the [all-contributors](https://github.com/all-contributors/all-contributors) specification. Contributions of any kind welcome!
