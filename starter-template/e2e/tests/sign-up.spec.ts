import { test, expect } from "../fixtures";
import Database from "better-sqlite3";

test("Happy Path", async ({ signUpPage, dashboardHomePage }) => {
  test.step("Ensure homer user doesn't exist", deleteCarl);
  await signUpPage.visit();
  await expect(signUpPage).toBeCurrentPage();

  await signUpPage.submit("carl@springfieldnuclear.com", "password1234");

  await expect(dashboardHomePage).toBeCurrentPage();

  const accounts = await getCarl();
  const createdAccount = accounts[0];
  expect(createdAccount).toEqual(
    expect.objectContaining({
      email: "carl@springfieldnuclear.com",
    }),
  );
  const [createdUser] = await getCarl();

  expect(createdUser).toEqual(
    expect.objectContaining({
      id: expect.any(Number),
    }),
  );

  test.step("Cleanup", deleteCarl);
});

test("User exists path", async ({ signUpPage }) => {
  await signUpPage.visit();
  await expect(signUpPage).toBeCurrentPage();

  await signUpPage.submit("carl@springfieldnuclear.com", "password1234");

  await expect(signUpPage).toBeCurrentPage();
  await expect(signUpPage.error).toHaveText(
    "User with that email already exists",
  );
});

// The app serves TRAM_ENV=development, so these assertions read the same file
// its handlers write. Paths are relative to e2e/, where playwright runs.
const db = new Database("../db/development.db");

const CARL = "carl@springfieldnuclear.com";

function getCarl() {
  return db.prepare("SELECT * FROM users WHERE email = ?").all(CARL);
}

function deleteCarl() {
  return db.prepare("DELETE FROM users WHERE email = ?").run(CARL);
}
