import { Locator, Page as PlaywrightPage } from "@playwright/test";

export abstract class Page {
  protected page: PlaywrightPage;
  protected url: string | undefined;

  constructor(page: PlaywrightPage) {
    this.page = page;
  }

  get currentPageLocators() {
    return null as null | Locator[];
  }

  async visit() {
    if (!this.url) {
      throw new Error(
        `Page ${this.constructor.name} does not have a registered 'url'.`,
      );
    }
    return await this.page.goto(this.url);
  }
}
