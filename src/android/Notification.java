/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
*/
package org.apache.cordova.dialogs;

import android.content.Context;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.widget.TextViewCompat;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.LOG;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;


/**
 * This class provides access to notifications on the device.
 * <p>
 * Material Design 3 version: every dialog is built with MaterialAlertDialogBuilder,
 * so it follows the activity theme (DayNight, colorPrimary, shapes, typography).
 * <p>
 * Be aware that this implementation gets called on
 * navigator.notification.{alert|confirm|prompt}, and that there is a separate
 * implementation in org.apache.cordova.CordovaChromeClient that gets
 * called on a simple window.{alert|confirm|prompt}.
 */
public class Notification extends CordovaPlugin {

    private static final String LOG_TAG = "Notification";

    private static final String ACTION_BEEP           = "beep";
    private static final String ACTION_ALERT          = "alert";
    private static final String ACTION_CONFIRM        = "confirm";
    private static final String ACTION_PROMPT         = "prompt";
    private static final String ACTION_ACTIVITY_START = "activityStart";
    private static final String ACTION_ACTIVITY_STOP  = "activityStop";
    private static final String ACTION_PROGRESS_START = "progressStart";
    private static final String ACTION_PROGRESS_VALUE = "progressValue";
    private static final String ACTION_PROGRESS_STOP  = "progressStop";
    private static final String ACTION_DISMISS_PREVIOUS  = "dismissPrevious";
    private static final String ACTION_DISMISS_ALL  = "dismissAll";

    private static final long BEEP_TIMEOUT   = 5000;
    private static final long BEEP_WAIT_TINE = 100;

    private ArrayList<AlertDialog> dialogs = new ArrayList<>();

    public int confirmResult = -1;
    public AlertDialog spinnerDialog = null;
    public AlertDialog progressDialog = null;
    private LinearProgressIndicator progressIndicator = null;

    /**
     * Constructor.
     */
    public Notification() {
    }

    /**
     * Executes the request and returns PluginResult.
     *
     * @param action            The action to execute.
     * @param args              JSONArray of arguments for the plugin.
     * @param callbackContext   The callback context used when calling back into JavaScript.
     * @return                  True when the action was valid, false otherwise.
     */
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        /*
         * Don't run any of these if the current activity is finishing
         * in order to avoid android.view.WindowManager$BadTokenException
         * crashing the app. Just return true here since false should only
         * be returned in the event of an invalid action.
         */
        if (this.cordova.getActivity().isFinishing()) return true;

        switch (action) {
            case ACTION_BEEP:
                this.beep(args.getLong(0));
                break;
            case ACTION_ALERT:
                this.alert(args.getString(0), args.getString(1), args.getString(2), callbackContext);
                return true;
            case ACTION_CONFIRM:
                this.confirm(args.getString(0), args.getString(1), args.getJSONArray(2), callbackContext);
                return true;
            case ACTION_PROMPT:
                this.prompt(args.getString(0), args.getString(1), args.getJSONArray(2), args.getString(3), callbackContext);
                return true;
            case ACTION_ACTIVITY_START:
                this.activityStart(args.getString(0), args.getString(1));
                break;
            case ACTION_ACTIVITY_STOP:
                this.activityStop();
                break;
            case ACTION_PROGRESS_START:
                this.progressStart(args.getString(0), args.getString(1));
                break;
            case ACTION_PROGRESS_VALUE:
                this.progressValue(args.getInt(0));
                break;
            case ACTION_PROGRESS_STOP:
                this.progressStop();
                break;
            case ACTION_DISMISS_PREVIOUS:
                this.dismissPrevious(callbackContext);
                break;
            case ACTION_DISMISS_ALL:
                this.dismissAll(callbackContext);
                break;
            default:
                return false;
        }

        // Only alert and confirm are async.
        callbackContext.success();
        return true;
    }

    //--------------------------------------------------------------------------
    // LOCAL METHODS
    //--------------------------------------------------------------------------

    /**
     * Beep plays the default notification ringtone.
     *
     * @param count     Number of times to play notification
     */
    public void beep(final long count) {
        cordova.getThreadPool().execute(() -> {
            Uri ringtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone notification = RingtoneManager.getRingtone(cordova.getActivity().getBaseContext(), ringtone);

            // If phone is not set to silent mode
            if (notification != null) {
                for (long i = 0; i < count; ++i) {
                    notification.play();
                    long timeout = BEEP_TIMEOUT;
                    while (notification.isPlaying() && (timeout > 0)) {
                        timeout = timeout - BEEP_WAIT_TINE;
                        try {
                            Thread.sleep(BEEP_WAIT_TINE);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }
        });
    }

    /**
     * Builds and shows a native Android alert with given Strings
     * @param message           The message the alert should display
     * @param title             The title of the alert
     * @param buttonLabel       The label of the button
     * @param callbackContext   The callback context
     */
    public synchronized void alert(final String message, final String title, final String buttonLabel, final CallbackContext callbackContext) {
        final CordovaInterface cordova = this.cordova;

        Runnable runnable = () -> {
            MaterialAlertDialogBuilder dlg = createDialog(cordova);
            dlg.setMessage(message);
            dlg.setTitle(title);
            dlg.setCancelable(true);
            dlg.setPositiveButton(buttonLabel,
                    (dialog, which) -> {
                        dialog.dismiss();
                        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, 0));
                    });
            dlg.setOnCancelListener(dialog -> {
                dialog.dismiss();
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, 0));
            });

            changeTextDirection(dlg);
        };
        this.cordova.getActivity().runOnUiThread(runnable);
    }

    /**
     * Builds and shows a native Android confirm dialog with given title, message, buttons.
     * This dialog only shows up to 3 buttons.  Any labels after that will be ignored.
     * The index of the button pressed will be returned to the JavaScript callback identified by callbackId.
     *
     * @param message           The message the dialog should display
     * @param title             The title of the dialog
     * @param buttonLabels      A comma separated list of button labels (Up to 3 buttons)
     * @param callbackContext   The callback context.
     */
    public synchronized void confirm(final String message, final String title, final JSONArray buttonLabels, final CallbackContext callbackContext) {
        final CordovaInterface cordova = this.cordova;

        Runnable runnable = () -> {
            MaterialAlertDialogBuilder dlg = createDialog(cordova);
            dlg.setMessage(message);
            dlg.setTitle(title);
            dlg.setCancelable(true);

            // First button
            if (buttonLabels.length() > 0) {
                try {
                    dlg.setNegativeButton(buttonLabels.getString(0),
                            (dialog, which) -> {
                                dialog.dismiss();
                                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, 1));
                            });
                } catch (JSONException e) {
                    LOG.d(LOG_TAG, "JSONException on first button.");
                }
            }

            // Second button
            if (buttonLabels.length() > 1) {
                try {
                    dlg.setNeutralButton(buttonLabels.getString(1),
                            (dialog, which) -> {
                                dialog.dismiss();
                                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, 2));
                            });
                } catch (JSONException e) {
                    LOG.d(LOG_TAG, "JSONException on second button.");
                }
            }

            // Third button
            if (buttonLabels.length() > 2) {
                try {
                    dlg.setPositiveButton(buttonLabels.getString(2),
                            (dialog, which) -> {
                                dialog.dismiss();
                                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, 3));
                            });
                } catch (JSONException e) {
                    LOG.d(LOG_TAG, "JSONException on third button.");
                }
            }
            dlg.setOnCancelListener(dialog -> {
                dialog.dismiss();
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, 0));
            });

            changeTextDirection(dlg);
        };
        this.cordova.getActivity().runOnUiThread(runnable);
    }

    /**
     * Builds and shows a native Android prompt dialog with given title, message, buttons.
     * This dialog only shows up to 3 buttons.  Any labels after that will be ignored.
     * The following results are returned to the JavaScript callback identified by callbackId:
     *     buttonIndex			Index number of the button selected
     *     input1				The text entered in the prompt dialog box
     *
     * @param message           The message the dialog should display
     * @param title             The title of the dialog
     * @param buttonLabels      A comma separated list of button labels (Up to 3 buttons)
     * @param callbackContext   The callback context.
     */
    public synchronized void prompt(final String message, final String title, final JSONArray buttonLabels, final String defaultText, final CallbackContext callbackContext) {

        final CordovaInterface cordova = this.cordova;

        Runnable runnable = () -> {
            Context ctx = cordova.getActivity();

            // Material outlined text field. Text and outline colors come from the theme,
            // so the input stays readable in both light and dark mode
            // (replaces the old CB-11677 workaround that forced dark text).
            TextInputLayout inputLayout = new TextInputLayout(ctx, null,
                    com.google.android.material.R.attr.textInputOutlinedStyle);
            final TextInputEditText promptInput = new TextInputEditText(inputLayout.getContext());
            promptInput.setInputType(InputType.TYPE_CLASS_TEXT);
            promptInput.setSingleLine(true);
            promptInput.setText(defaultText);
            if (defaultText != null) promptInput.setSelection(defaultText.length());
            inputLayout.addView(promptInput, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            FrameLayout container = new FrameLayout(ctx);
            container.setPadding(dp(ctx, 24), dp(ctx, 8), dp(ctx, 24), 0);
            container.addView(inputLayout, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            MaterialAlertDialogBuilder dlg = createDialog(cordova);
            dlg.setMessage(message);
            dlg.setTitle(title);
            dlg.setCancelable(true);

            dlg.setView(container);

            final JSONObject result = new JSONObject();

            // First button
            if (buttonLabels.length() > 0) {
                try {
                    dlg.setNegativeButton(buttonLabels.getString(0),
                            (dialog, which) -> {
                                dialog.dismiss();
                                try {
                                    result.put("buttonIndex", 1);
                                    result.put("input1", inputValue(promptInput, defaultText));
                                } catch (JSONException e) {
                                    LOG.d(LOG_TAG, "JSONException on first button.", e);
                                }
                                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, result));
                            });
                } catch (JSONException e) {
                    LOG.d(LOG_TAG, "JSONException on first button.");
                }
            }

            // Second button
            if (buttonLabels.length() > 1) {
                try {
                    dlg.setNeutralButton(buttonLabels.getString(1),
                            (dialog, which) -> {
                                dialog.dismiss();
                                try {
                                    result.put("buttonIndex", 2);
                                    result.put("input1", inputValue(promptInput, defaultText));
                                } catch (JSONException e) {
                                    LOG.d(LOG_TAG, "JSONException on second button.", e);
                                }
                                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, result));
                            });
                } catch (JSONException e) {
                    LOG.d(LOG_TAG, "JSONException on second button.");
                }
            }

            // Third button
            if (buttonLabels.length() > 2) {
                try {
                    dlg.setPositiveButton(buttonLabels.getString(2),
                            (dialog, which) -> {
                                dialog.dismiss();
                                try {
                                    result.put("buttonIndex", 3);
                                    result.put("input1", inputValue(promptInput, defaultText));
                                } catch (JSONException e) {
                                    LOG.d(LOG_TAG, "JSONException on third button.", e);
                                }
                                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, result));
                            });
                } catch (JSONException e) {
                    LOG.d(LOG_TAG, "JSONException on third button.");
                }
            }
            dlg.setOnCancelListener(dialog -> {
                dialog.dismiss();
                try {
                    result.put("buttonIndex", 0);
                    result.put("input1", inputValue(promptInput, defaultText));
                } catch (JSONException e) { e.printStackTrace(); }
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, result));
            });

            changeTextDirection(dlg);
            promptInput.requestFocus();
        };
        this.cordova.getActivity().runOnUiThread(runnable);
    }

    /**
     * Close previously opened dialog
     */
    public synchronized void dismissPrevious(final CallbackContext callbackContext) {
        if (!dialogs.isEmpty()) {
            dialogs.remove(dialogs.size() - 1).dismiss();
            callbackContext.success();
        } else {
            callbackContext.error("No previously opened dialog to dismiss");
        }
    }

    /**
     * Close any open dialog.
     */
    public synchronized void dismissAll(final CallbackContext callbackContext) {
        if (!dialogs.isEmpty()) {
            for (AlertDialog dialog : dialogs) {
                dialog.dismiss();
            }
            dialogs = new ArrayList<>();
            callbackContext.success();
        } else {
            callbackContext.error("No previously opened dialogs to dismiss");
        }
    }

    /**
     * Show the spinner.
     *
     * @param title     Title of the dialog
     * @param message   The message of the dialog
     */
    public synchronized void activityStart(final String title, final String message) {
        if (this.spinnerDialog != null) {
            this.spinnerDialog.dismiss();
            this.spinnerDialog = null;
        }
        final Notification notification = this;
        final CordovaInterface cordova = this.cordova;
        Runnable runnable = () -> {
            Context ctx = cordova.getActivity();

            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(ctx, 24), dp(ctx, 20), dp(ctx, 24), dp(ctx, 20));

            CircularProgressIndicator indicator = new CircularProgressIndicator(ctx);
            indicator.setIndeterminate(true);
            row.addView(indicator, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            if (message != null && !message.isEmpty()) {
                TextView text = new TextView(ctx);
                TextViewCompat.setTextAppearance(text,
                        com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
                text.setTextColor(MaterialColors.getColor(text,
                        com.google.android.material.R.attr.colorOnSurfaceVariant));
                text.setText(message);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                lp.setMarginStart(dp(ctx, 16));
                row.addView(text, lp);
            }

            MaterialAlertDialogBuilder dlg = createDialog(cordova);
            if (title != null && !title.isEmpty()) dlg.setTitle(title);
            dlg.setView(row);
            dlg.setCancelable(true);
            dlg.setOnCancelListener(dialog -> notification.spinnerDialog = null);
            notification.spinnerDialog = dlg.show();
        };
        this.cordova.getActivity().runOnUiThread(runnable);
    }

    /**
     * Stop spinner.
     */
    public synchronized void activityStop() {
        if (this.spinnerDialog != null) {
            this.spinnerDialog.dismiss();
            this.spinnerDialog = null;
        }
    }

    /**
     * Show the progress dialog.
     *
     * @param title     Title of the dialog
     * @param message   The message of the dialog
     */
    public synchronized void progressStart(final String title, final String message) {
        if (this.progressDialog != null) {
            this.progressDialog.dismiss();
            this.progressDialog = null;
            this.progressIndicator = null;
        }
        final Notification notification = this;
        final CordovaInterface cordova = this.cordova;
        Runnable runnable = () -> {
            Context ctx = cordova.getActivity();

            LinearProgressIndicator indicator = new LinearProgressIndicator(ctx);
            indicator.setMax(100);
            indicator.setProgress(0);

            FrameLayout container = new FrameLayout(ctx);
            container.setPadding(dp(ctx, 24), dp(ctx, 16), dp(ctx, 24), dp(ctx, 16));
            container.addView(indicator, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            MaterialAlertDialogBuilder dlg = createDialog(cordova);
            if (title != null && !title.isEmpty()) dlg.setTitle(title);
            if (message != null && !message.isEmpty()) dlg.setMessage(message);
            dlg.setView(container);
            dlg.setCancelable(true);
            dlg.setOnCancelListener(dialog -> {
                notification.progressDialog = null;
                notification.progressIndicator = null;
            });
            notification.progressIndicator = indicator;
            notification.progressDialog = dlg.show();
        };
        this.cordova.getActivity().runOnUiThread(runnable);
    }

    /**
     * Set value of progress bar.
     *
     * @param value     0-100
     */
    public synchronized void progressValue(final int value) {
        final Notification notification = this;
        this.cordova.getActivity().runOnUiThread(() -> {
            if (notification.progressIndicator != null) {
                notification.progressIndicator.setProgressCompat(value, true);
            }
        });
    }

    /**
     * Stop progress dialog.
     */
    public synchronized void progressStop() {
        if (this.progressDialog != null) {
            this.progressDialog.dismiss();
            this.progressDialog = null;
            this.progressIndicator = null;
        }
    }

    /**
     * Material 3 dialog builder. It inherits the activity theme
     * (Theme.Material3.DayNight...), so it follows dark mode and app colors.
     */
    private MaterialAlertDialogBuilder createDialog(CordovaInterface cordova) {
        return new MaterialAlertDialogBuilder(cordova.getActivity());
    }

    private void changeTextDirection(MaterialAlertDialogBuilder dlg) {
        AlertDialog dialog = dlg.show();
        dialogs.add(dialog);
        TextView messageview = dialog.findViewById(android.R.id.message);
        if (messageview != null) {
            messageview.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        }
    }

    private static String inputValue(TextInputEditText input, String defaultText) {
        String text = input.getText() != null ? input.getText().toString() : "";
        return text.trim().isEmpty() ? defaultText : text;
    }

    private static int dp(Context ctx, int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                ctx.getResources().getDisplayMetrics());
    }
}