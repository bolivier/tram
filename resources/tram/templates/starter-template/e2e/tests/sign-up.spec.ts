import { test, expect } from "../fixtures";
import postgres from "postgres";

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

const sql = postgres({
  host: "localhost",
  database: "sample_app_development",
  port: 5433,
  fetch_types: false,
});

async function getCarl() {
  return await sql`SELECT * FROM users where email = 'carl@springfieldnuclear.com'`;
}

async function deleteCarl() {
  return await sql`delete from users where email = 'carl@springfieldnuclear.com'`;
}
