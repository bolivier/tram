import { Page } from "./Page";

export class SignInPage extends Page {
  url = "/sign-in";

  get currentPageLocators() {
    return [this.emailField, this.passwordField, this.signInButton];
  }

  get error() {
    return this.page.locator("#error");
  }

  get emailField() {
    return this.page.getByLabel("Email");
  }

  get passwordField() {
    return this.page.getByLabel("Password");
  }

  get signInButton() {
    return this.page.getByRole("button", { name: "Sign In" });
  }

  async login(email: string, password: string) {
    await this.emailField.fill(email);
    await this.passwordField.fill(password);
    await this.signInButton.click();
  }
}
