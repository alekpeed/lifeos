// The pure half of the per-item FCM push (§7 D-5 Phase 2): what counts as urgent, and
// what the message looks like. No network, no Deno, no Supabase — so it can be unit
// tested, which matters more here than in most places because nothing else in this
// path can be exercised without a real device holding a real token.

import { type Item, subjectOf } from '../telegram-digest/digest.ts';

export { KEYS, itemsFrom, type Item } from '../telegram-digest/digest.ts';

// The division of labour in §7 D-5: Telegram carries the daily look-ahead, FCM carries
// the individual urgent thing. "Urgent" is therefore narrower than the digest's window
// — due today or already past. A push that arrives three days early is a digest with a
// worse delivery mechanism.
export function isUrgent(item: Item): boolean {
  return item.when <= 0;
}

// A stale push is worse than a late one: an item can be resolved on the phone and the
// notification for it still be in flight. One per record per day, keyed by the same
// subject the notification carries, is what the fcm_sent table records.
export function sentKey(item: Item, day: string): string {
  return `${subjectOf(item) || `${item.key}|${item.title}`}@${day}`;
}

export function titleFor(item: Item): string {
  return item.when < 0 ? `Overdue: ${item.title}` : item.title;
}

export function bodyFor(item: Item): string {
  if (item.when < -1) return `${item.kind} — ${-item.when} days overdue`;
  if (item.when === -1) return `${item.kind} — overdue since yesterday`;
  return `${item.kind} — due today`;
}

// DATA-ONLY, deliberately, and this is the part of §7 D-5 worth being precise about.
//
// The decision doc says "only FCM can carry notification actions". FCM does not carry
// actions at all — there is no field for them in the v1 API. What it carries is a
// payload, and the app builds the notification from it, which is how the buttons get
// there. So the message must NOT contain a `notification` block: with one, Android
// draws the notification itself while the app is backgrounded, the app never sees the
// payload, and the buttons never appear. A data-only message is always handed to the
// app's messaging service, which calls postReminder(title, body, subject) and gets the
// same Done / Tomorrow buttons a local alarm produces.
export function buildMessage(item: Item, token: string): Record<string, unknown> {
  return {
    message: {
      token,
      data: {
        title: titleFor(item),
        body: bodyFor(item),
        // "<storage key>|<record id>", resolved by push/Actions.kt on the device.
        subject: subjectOf(item),
      },
      android: {
        // Data-only messages are throttled unless they say they are worth waking for.
        priority: 'high',
      },
    },
  };
}

// FCM's way of saying a token belongs to an app that has been uninstalled or whose
// registration has rolled. Anything else is a transient failure and the row stays.
export function isDeadToken(status: number, body: string): boolean {
  if (status === 404) return true;
  if (status !== 400 && status !== 403) return false;
  return /UNREGISTERED|INVALID_ARGUMENT|SenderId mismatch/i.test(body);
}
