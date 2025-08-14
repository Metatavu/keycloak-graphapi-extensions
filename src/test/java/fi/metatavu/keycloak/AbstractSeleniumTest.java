package fi.metatavu.keycloak;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Abstract base class for Selenium tests
 */
public class AbstractSeleniumTest {

    /**
     * Waits for an element to be clickable and clicks it.
     *
     * If the element is not clickable within 60 seconds, the method will throw an exception.
     *
     * @param driver web driver
     * @param by element locator
     */
    protected void waitButtonAndClick(WebDriver driver, By by) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(60));
        WebElement element = wait.until(ExpectedConditions.elementToBeClickable(by));
        element.click();
    }

    /**
     * Waits for an element to have a specific text.
     *
     * If the element does not have the text within 60 seconds, the method will throw an exception.
     *
     * @param driver web driver
     * @param by element locator
     */
    protected void waitText(WebDriver driver, By by, String text) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(60));
        wait.until(ExpectedConditions.textToBe(by, text));
    }

    /**
     * Waits for an element to be clickable and types text into it.
     *
     * If the element is not clickable within 60 seconds, the method will throw an exception.
     *
     * @param driver web driver
     * @param by element locator
     * @param text text to type
     */
    protected void waitInputAndType(WebDriver driver, By by, String text) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(60));
        WebElement element = wait.until(ExpectedConditions.elementToBeClickable(by));
        element.sendKeys(text);
    }

    /**
     * Waits for an input to have a specific value.
     *
     * If the element is not clickable within 60 seconds, the method will throw an exception.
     *
     * @param driver web driver
     * @param by element locator
     * @param text text to type
     */
    protected void waitAndAssertInputValue(WebDriver driver, By by, String text) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(60));
        WebElement element = wait.until(ExpectedConditions.elementToBeClickable(by));
        assertEquals(text, element.getAttribute("value"));
    }

    /**
     * Returns a By locator for a data-testid attribute
     *
     * @param dataTestId data-testid value
     * @return By locator
     */
    protected By byDataTestId(String dataTestId) {
        return By.cssSelector("[data-testid='" + dataTestId + "']");
    }

    /**
     * Logs user out
     *
     * @param driver web driver
     */
    protected void logout(RemoteWebDriver driver) {
        driver.get(getAccountUrl());
        waitButtonAndClick(driver, By.cssSelector("button.pf-v5-c-menu-toggle"));
        waitButtonAndClick(driver, By.cssSelector("button.pf-v5-c-menu__item"));
        waitButtonAndClick(driver, By.id("social-oidc"));
    }

    /**
     * Returns the account URL for the Keycloak instance
     *
     * @return account URL
     */
    protected String getAccountUrl() {
        return "http://keycloak:8080/realms/test/account";
    }

}
