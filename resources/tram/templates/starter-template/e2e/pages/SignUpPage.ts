import { Page } from "./Page";

export class SignUpPage extends Page {
  async visit() {
    await this.page.goto("http://localhost:1337/sign-up");
  }

  get currentPageLocators() {
    return [this.emailField, this.passwordField, this.signUpButton];
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

  get signUpButton() {
    return this.page.getByRole("button", { name: "Create" });
  }

  async submit(email: string, password: string) {
    await this.emailField.fill(email);
    await this.passwordField.fill(password);

    await this.signUpButton.click();
  }
}
