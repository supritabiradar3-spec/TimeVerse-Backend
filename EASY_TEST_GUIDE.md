# TimeVerse - Easy Test Guide

## What is fixed in this copy

- Expired JWTs are detected in the browser before protected API calls.
- A 401, or a 403 caused by an expired JWT, clears the stale session and sends the user back to login instead of leaving Orders/Cart/Wishlist stuck.
- A fresh login clears any old token first.
- Cart endpoints are protected consistently because the backend CartService requires a JWT.
- Cart and wishlist product images are constrained so watches are not excessively zoomed.
- A light/dark theme toggle is available in the navbar and the selected theme is remembered.
- Existing cart, wishlist, orders, payment, admin, customer, and review flows are preserved.

## Important: the 403 you saw

If the browser shows a message such as:

`JWT expired ...`

that token is genuinely expired. Do **not** keep retrying the Orders page with the same token.

### One-time cleanup after replacing the frontend

1. Open the customer site.
2. Press `F12` -> `Application` -> `Local Storage`.
3. Remove the old `token` (or use Logout if the site still lets you).
4. Log in again.
5. Open **My Orders**, **Wishlist**, and **Cart**.

The new frontend will also detect an expired JWT automatically on the next request.

## Easy test order

1. Start MySQL.
2. Start the backend on port `8080`.
3. Confirm the console says `Tomcat started on port 8080`.
4. Serve the frontend with a local HTTP server.
5. Register a new customer and complete the registration OTP.
6. Log in as CUSTOMER with email/password.
7. Open Products -> add one watch to Wishlist -> open Wishlist -> remove it -> add another to Cart.
8. Open Cart -> increase/decrease quantity -> remove an item.
9. Checkout -> select/add address -> place order -> verify the order appears in My Orders.
10. Test payment with the configured Razorpay test setup.
11. Log in as ADMIN separately and test dashboard, Users, Products, and Orders.
12. Mark a delivered order as `DELIVERED` and then test the customer review box in Profile.
13. Test Forgot Password -> receive OTP -> verify OTP -> set a new password.
14. Test the light/dark toggle from the navbar.

## If OTP is not received

Look at the backend console immediately after pressing **Send OTP**.

For SMTP success you should see:

`JavaMailSender.send() completed successfully without exception`

If that line is missing, the problem is SMTP delivery/configuration rather than the frontend.

Also check Gmail Spam/Promotions and verify that the `.env` used by the running Java process contains the correct SMTP username and App Password.
