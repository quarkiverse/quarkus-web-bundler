package io.quarkiverse.web.bundler.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;

import io.quarkiverse.playwright.InjectPlaywright;
import io.quarkiverse.playwright.WithPlaywright;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.assertions.PlaywrightAssertions;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@WithPlaywright
public class WebTest {
    @InjectPlaywright
    BrowserContext context;

    @TestHTTPResource("/")
    URL url;

    @Test
    void root() {
        final Page page = context.newPage();
        Response response = page.navigate(url.toString());
        Assertions.assertEquals("OK", response.statusText());

        page.waitForLoadState();

        PlaywrightAssertions.assertThat(page).hasTitle("QWA");

        page.waitForCondition(() -> page.innerText("#message")
                .equals("Unleash the script!!!!"));

        assertThat(
                page.querySelector("#message").evaluate("element => getComputedStyle(element).color"))
                .isEqualTo("rgb(255, 127, 80)");

        assertThat(
                page.querySelector(".calendar h1").evaluate("element => getComputedStyle(element).color"))
                .isEqualTo("rgb(0, 0, 255)");

        assertThat(
                page.querySelector(".calendar h1").evaluate("element => getComputedStyle(element).backgroundColor"))
                .isEqualTo("rgb(255, 0, 0)");

        page.click("#page1");
        page.waitForLoadState();

        PlaywrightAssertions.assertThat(page).hasTitle("Page 1");

        page.waitForCondition(() -> page.innerText("#message")
                .equals("This is page 1"));

        assertThat(
                page.querySelector("#message").evaluate("element => getComputedStyle(element).color"))
                .isEqualTo("rgb(0, 191, 255)");

    }

}
