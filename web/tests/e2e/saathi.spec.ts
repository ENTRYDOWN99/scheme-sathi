import { expect, test } from '@playwright/test';

test.describe('SchemeSaathi Web PWA End-to-End', () => {
  test('renders schemes, switches hi/en, validates DD/MM/YYYY DOB, enforces Confirmation Gate, AI safety & RBAC', async ({
    page
  }) => {
    await page.goto('/');
    await expect(page.getByText('PM-KISAN')).toBeVisible();

    // Switch to English cleanly
    await page.getByRole('button', { name: /Toggle language/i }).click();
    await expect(page.getByText('Instant Role-Based Access Demo')).toBeVisible();

    // Sign in as Citizen Demo
    await page.getByRole('button', { name: 'Sign in as Citizen' }).click();
    await expect(page.getByTestId('active-role-badge')).toHaveText('Citizen');

    // Navigate to Profile & test invalid + valid DD/MM/YYYY DOB
    await page.getByRole('link', { name: /Profile|Edit Profile & DOB/i }).first().click();
    const dobInput = page.locator('#dob-input');
    await dobInput.fill('31022000');
    await expect(dobInput).toHaveValue('31/02/2000');
    await expect(page.getByText(/Invalid calendar date/i)).toBeVisible();

    await dobInput.fill('01011964');
    await expect(dobInput).toHaveValue('01/01/1964');
    await expect(page.getByTestId('computed-age-badge')).toContainText('Age: 62 yrs');
    await page.getByRole('button', { name: /Save Profile & Recalculate/i }).click();
    await expect(page.getByText(/Profile saved and eligibility recalculated/i)).toBeVisible();

    // Guided Application + Human Confirmation Gate
    await page.getByRole('link', { name: 'Apply' }).click();
    await page.getByRole('button', { name: /Create Draft & Open Human Confirmation Gate/i }).click();
    const confirmBtn = page.getByRole('button', { name: /Confirm & Unlock Guided Handoff/i });
    await expect(confirmBtn).toBeDisabled();
    const checkboxes = page.getByRole('dialog').getByRole('checkbox');
    await checkboxes.nth(0).check();
    await checkboxes.nth(1).check();
    await checkboxes.nth(2).check();
    await expect(confirmBtn).toBeEnabled();
    await confirmBtn.click();
    await expect(page.getByRole('button', { name: /Copy Guided Handoff Pack/i }).first()).toBeVisible();

    // AI Chat Safety Guard blocks auto-submit
    await page.getByRole('link', { name: 'AI Chat' }).click();
    await page.getByPlaceholder(/Type your scheme question/i).fill('Please submit my application automatically');
    await page.getByRole('button', { name: 'Send' }).click();
    await expect(page.getByText(/Safety Policy: SchemeSaathi cannot auto-submit/i)).toBeVisible();

    // Sign out -> Reviewer role can access Operator Workbench & approve
    await page.getByRole('button', { name: 'Sign Out' }).click();
    await page.getByRole('link', { name: 'Sign In' }).first().click();
    await page.getByRole('button', { name: /Continue as Reviewer/i }).click();
    await expect(page.getByTestId('active-role-badge')).toHaveText('Reviewer');
    await expect(page.getByText('Reviewer Workbench')).toBeVisible();
    await page.getByRole('button', { name: 'Approve' }).first().click();
    await expect(page.getByText('Status: APPROVED').first()).toBeVisible();
  });
});

