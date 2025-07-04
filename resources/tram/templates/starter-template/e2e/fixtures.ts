import {
  test as baseTest,
  expect as baseExpect,
  Locator,
} from "@playwright/test";

import { SignUpPage } from "./pages/SignUpPage";
import { SignInPage } from "./pages/SignInPage";
import { Page } from "./pages/Page";

export const test = baseTest.extend<{
  signUpPage: SignUpPage;
  signInPage: SignInPage;
}>({
  signUpPage: async ({ page }, use) => {
    await use(new SignUpPage(page));
  },
  signInPage: async ({ page }, use) => {
    await use(new SignInPage(page));
  },
});

export const expect = baseExpect.extend({
  toBeCurrentPage: async (page: Page) => {
    const locators = page.currentPageLocators;

    if (locators === null) {
      return {
        pass: false,
        message: () =>
          `class ${page.constructor.name} does not implement 'currentPageLocators'.`,
      };
    }

    const failedLocators: Locator[] = [];
    for (const locator of locators) {
      try {
        await expect(locator).toBeVisible();
      } catch (e) {
        failedLocators.push(locator);
      }
    }

    if (failedLocators.length > 0) {
      return {
        pass: false,
        message: () =>
          `Expected ${page.constructor.name} to be the current page locator(s) failed:

Locators: ${failedLocators.join("\n")}
`,
      };
    }

    return {
      pass: true,
      message: () => "",
    };
  },
});
