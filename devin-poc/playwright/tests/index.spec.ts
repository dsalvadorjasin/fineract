import { test } from '@playwright/test';

test.describe.configure({ mode: 'serial' });

require('./maker-withdrawal.spec');
require('./checker-approve.spec');
require('./checker-reject.spec');
require('./negative-max-withdrawal.spec');
