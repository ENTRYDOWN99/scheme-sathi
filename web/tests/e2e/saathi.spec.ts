import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';

async function assertNoAxeViolations(page: Page, routeLabel: string) {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze();
  expect(
    results.violations,
    `Expected zero WCAG AA axe violations on ${routeLabel}, got: ${JSON.stringify(
      results.violations.map((v) => ({ id: v.id, impact: v.impact, nodes: v.nodes.length }))
    )}`
  ).toEqual([]);
}

test.describe('SchemeSaathi Web PWA Hardened Verification', () => {
  test('covers hi/en across every page, OTP & password login failures, AI error states, all 4 roles & axe accessibility', async ({
    page
  }) => {
    // 1. Verify OTP & Password login failures + successful OTP verification
    await page.goto('/login');
    await expect(page.getByText('OTP से सुरक्षित प्रवेश')).toBeVisible();
    await assertNoAxeViolations(page, '/login (hi)');

    // Switch to English on /login
    await page.getByRole('button', { name: /Toggle language/i }).click();
    await expect(page.getByText('Sign In with One-Time Passcode')).toBeVisible();
    await assertNoAxeViolations(page, '/login (en)');

    // Wrong password failure
    await page.getByRole('button', { name: 'Password Login' }).click();
    await page.locator('#auth-identifier').fill('+919800000001');
    await page.locator('#auth-password').fill('WrongPass123!');
    await page.locator('form button[type="submit"]').click();
    await expect(page.getByRole('alert')).toBeVisible();

    // OTP send + wrong OTP failure + valid OTP sign-in
    await page.getByRole('button', { name: /Phone \/ Email OTP/i }).click();
    await page.getByRole('button', { name: 'Send OTP' }).click();
    const otpHintStrong = page.locator('strong');
    await expect(otpHintStrong).toBeVisible();
    const realOtp = (await otpHintStrong.innerText()).trim();

    await page.locator('#otp-code-input').fill('000000');
    await page.getByRole('button', { name: 'Verify & Sign In' }).click();
    await expect(page.getByRole('alert')).toBeVisible();

    await page.locator('#otp-code-input').fill(realOtp);
    await page.getByRole('button', { name: 'Verify & Sign In' }).click();
    await expect(page.getByTestId('active-role-badge')).toHaveText('Citizen');

    // 2. Home (/), hi/en toggle & axe scan
    await expect(page.getByRole('heading', { name: /PM-KISAN/i })).toBeVisible();
    await assertNoAxeViolations(page, '/ (en)');
    await page.getByRole('button', { name: /Toggle language/i }).click();
    await expect(page.getByText('यह कोई आधिकारिक सरकारी ऐप नहीं है')).toBeVisible();
    await assertNoAxeViolations(page, '/ (hi)');
    // Back to English
    await page.getByRole('button', { name: /Toggle language/i }).click();

    // Verify CITIZEN is blocked by RBAC from /operator
    await page.goto('/operator');
    await expect(page.getByText(/RBAC Protected/i)).toBeVisible();
    await assertNoAxeViolations(page, '/operator (citizen blocked)');

    // 3. Profile (/profile) hi/en, invalid/valid DOB, & axe
    await page.goto('/profile');
    await assertNoAxeViolations(page, '/profile (en)');
    await page.getByRole('button', { name: /Toggle language/i }).click();
    await expect(page.getByText(/जन्म तिथि \(DD\/MM\/YYYY\)/i)).toBeVisible();
    await page.getByRole('button', { name: /Toggle language/i }).click();

    const dobInput = page.locator('#dob-input');
    await dobInput.fill('31022000');
    await expect(dobInput).toHaveValue('31/02/2000');
    await expect(page.getByText(/Invalid calendar date/i)).toBeVisible();

    await dobInput.fill('01011964');
    await expect(dobInput).toHaveValue('01/01/1964');
    await expect(page.getByTestId('computed-age-badge')).toContainText('Age: 62 yrs');
    await page.getByRole('button', { name: /Save Profile & Recalculate/i }).click();
    await expect(page.getByText(/Profile saved and eligibility recalculated/i)).toBeVisible();

    // 4. Apply (/apply) hi/en, Confirmation Gate + Handoff Pack & axe
    await page.goto('/apply');
    await assertNoAxeViolations(page, '/apply (en)');
    await page.getByRole('button', { name: /Toggle language/i }).click();
    await expect(page.getByRole('heading', { name: /मार्गदर्शित आवेदन ड्राफ़्ट/i })).toBeVisible();
    await page.getByRole('button', { name: /Toggle language/i }).click();

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

    // 5. AI Chat (/chat) hi/en, safety block & busy/network/generic error states & axe
    await page.goto('/chat');
    await assertNoAxeViolations(page, '/chat (en)');
    await page.getByRole('button', { name: /Toggle language/i }).click();
    await expect(page.getByRole('button', { name: 'भेजें' })).toBeVisible();
    await page.getByRole('button', { name: /Toggle language/i }).click();

    await page.getByPlaceholder(/Type your scheme question/i).fill('Please submit my application automatically');
    await page.getByRole('button', { name: 'Send' }).click();
    await expect(page.getByText(/Safety Policy: SchemeSaathi cannot auto-submit/i)).toBeVisible();

    // AI Busy (429)
    await page.route('**/api/v1/ai/chat', async (route) => {
      await route.fulfill({
        status: 429,
        contentType: 'application/json',
        body: JSON.stringify({ error: 'RATE_LIMITED', message: 'Quota exceeded' })
      });
    });
    await page.getByPlaceholder(/Type your scheme question/i).fill('Check quota');
    await page.getByRole('button', { name: 'Send' }).click();
    await expect(page.getByText(/AI is busy right now, please try again in a minute\./i).first()).toBeVisible();
    await page.unroute('**/api/v1/ai/chat');

    // AI Network failure
    await page.route('**/api/v1/ai/chat', async (route) => {
      await route.abort('internetdisconnected');
    });
    await page.getByPlaceholder(/Type your scheme question/i).fill('Check network');
    await page.getByRole('button', { name: 'Send' }).click();
    await expect(page.getByText(/Please check your internet connection and try again\./i).first()).toBeVisible();
    await page.unroute('**/api/v1/ai/chat');

    // AI Generic error (500)
    await page.route('**/api/v1/ai/chat', async (route) => {
      await route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({ error: 'INTERNAL_ERROR', message: 'Upstream failure' })
      });
    });
    await page.getByPlaceholder(/Type your scheme question/i).fill('Check fallback');
    await page.getByRole('button', { name: 'Send' }).click();
    await expect(page.getByText(/Unable to reach AI right now\. Showing offline rule summary below\./i).first()).toBeVisible();
    await page.unroute('**/api/v1/ai/chat');

    // 6. Alerts (/alerts) & Settings (/settings) hi/en & axe
    await page.goto('/alerts');
    await assertNoAxeViolations(page, '/alerts (en)');
    await page.getByRole('button', { name: /Toggle language/i }).click();
    await expect(page.getByText('सहयोगी कार्य और अलर्ट')).toBeVisible();
    await page.getByRole('button', { name: /Toggle language/i }).click();

    await page.goto('/settings');
    await assertNoAxeViolations(page, '/settings (en)');
    await page.getByRole('button', { name: /Toggle language/i }).click();
    await expect(page.getByText(/मेरा डेटा डाउनलोड करें/i)).toBeVisible();
    await page.getByRole('button', { name: /Toggle language/i }).click();

    // 7. Remaining 3 roles: CSC_OPERATOR, REVIEWER, SUPERVISOR + axe on /operator
    await page.getByRole('button', { name: 'Sign Out' }).click();
    await page.getByRole('link', { name: 'Sign In' }).first().click();
    await page.getByRole('button', { name: /Continue as CSC Operator/i }).click();
    await expect(page.getByTestId('active-role-badge')).toHaveText('CSC Operator');
    await expect(page.getByText('CSC Operator Workbench')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Approve' })).toHaveCount(0);
    await assertNoAxeViolations(page, '/operator (CSC Operator)');

    await page.getByRole('button', { name: 'Sign Out' }).click();
    await page.getByRole('link', { name: 'Sign In' }).first().click();
    await page.getByRole('button', { name: /Continue as Reviewer/i }).click();
    await expect(page.getByTestId('active-role-badge')).toHaveText('Reviewer');
    await expect(page.getByText('Reviewer Workbench')).toBeVisible();
    await assertNoAxeViolations(page, '/operator (Reviewer)');
    await page.getByRole('button', { name: 'Approve' }).first().click();
    await expect(page.getByText('Status: APPROVED').first()).toBeVisible();

    await page.getByRole('button', { name: 'Sign Out' }).click();
    await page.getByRole('link', { name: 'Sign In' }).first().click();
    await page.getByRole('button', { name: /Continue as Supervisor/i }).click();
    await expect(page.getByTestId('active-role-badge')).toHaveText('Supervisor');
    await expect(page.getByText('Supervisor Workbench')).toBeVisible();
    await expect(page.getByText('Audit Trail (PII Redacted)')).toBeVisible();
    await page.getByRole('button', { name: /Toggle language/i }).click();
    await expect(page.getByRole('heading', { name: /पर्यवेक्षक \(Supervisor\)/ })).toBeVisible();
    await page.getByRole('button', { name: /Toggle language/i }).click();
    await assertNoAxeViolations(page, '/operator (Supervisor)');
  });
});
