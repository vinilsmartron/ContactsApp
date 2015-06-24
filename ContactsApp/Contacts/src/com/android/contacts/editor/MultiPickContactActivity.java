/**
 * Copyright (C) 2014, The Linux Foundation. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.contacts.editor;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.AsyncQueryHandler;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContentProviderOperation;
import android.content.DialogInterface;
import android.content.IntentFilter;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.net.Uri.Builder;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.telephony.SubscriptionManager;
import android.text.Editable;
import android.text.format.DateUtils;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.contacts.activities.PeopleActivity;
import com.android.contacts.R;
import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.ContactPhotoManager.DefaultImageRequest;
import com.android.contacts.common.SimContactsConstants;
import com.android.contacts.common.SimContactsOperation;
import com.android.contacts.common.list.AccountFilterActivity;
import com.android.contacts.common.list.ContactListFilter;
import com.android.contacts.common.MoreContactUtils;
import com.android.contacts.common.model.account.SimAccountType;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class MultiPickContactActivity extends ListActivity implements
        View.OnClickListener, TextView.OnEditorActionListener,
        OnTouchListener, TextWatcher {
    private final static String TAG = "MultiPickContactActivity";
    private final static boolean DEBUG = true;

    private static final String SORT_ORDER = " desc";

    static final String[] CONTACTS_SUMMARY_PROJECTION = new String[] {
            Contacts._ID, // 0
            Contacts.DISPLAY_NAME_PRIMARY, // 1
            Contacts.DISPLAY_NAME_ALTERNATIVE, // 2
            Contacts.PHOTO_ID, // 3
            Contacts.LOOKUP_KEY, // 4
            RawContacts.ACCOUNT_TYPE, // 5
            RawContacts.ACCOUNT_NAME // 6
    };

    static final String[] CALL_LOG_PROJECTION = new String[] {
            Calls._ID,
            Calls.NUMBER,
            Calls.DATE,
            Calls.DURATION,
            Calls.TYPE,
            Calls.CACHED_NAME,
            Calls.CACHED_NUMBER_TYPE,
            Calls.CACHED_NUMBER_LABEL,
            Calls.PHONE_ACCOUNT_ID
    };

    static final String CONTACTS_SELECTION = Contacts.IN_VISIBLE_GROUP + "=1";

    static final String[] PHONES_PROJECTION = new String[] {
            Phone._ID, // 0
            Phone.TYPE, // 1
            Phone.LABEL, // 2
            Phone.NUMBER, // 3
            Phone.DISPLAY_NAME, // 4
            Phone.CONTACT_ID // 5
    };

    static final String PHONES_SELECTION = RawContacts.ACCOUNT_TYPE + "<>?";

    static final String[] PHONES_SELECTION_ARGS = {
            SimContactsConstants.ACCOUNT_TYPE_SIM
    };

    static final String[] EMAILS_PROJECTION = new String[] {
            Email._ID,
            Phone.DISPLAY_NAME,
            Email.ADDRESS,
    };

    private static final int SUMMARY_ID_COLUMN_INDEX = 0;
    private static final int SUMMARY_DISPLAY_NAME_PRIMARY_COLUMN_INDEX = 1;
    private static final int SUMMARY_DISPLAY_NAME_ALTERNATIVE_COLUMN_INDEX = 2;
    private static final int SUMMARY_PHOTO_ID_COLUMN_INDEX = 3;
    private static final int SUMMARY_LOOKUP_KEY_COLUMN_INDEX = 4;
    private static final int SUMMARY_ACCOUNT_TYPE = 5;
    private static final int SUMMARY_ACCOUNT_NAME = 6;

    //call log column
    private static final int ID_COLUMN_INDEX = 0;
    private static final int NUMBER_COLUMN_INDEX = 1;
    private static final int DATE_COLUMN_INDEX = 2;
    private static final int DURATION_COLUMN_INDEX = 3;
    private static final int CALL_TYPE_COLUMN_INDEX = 4;
    private static final int CALLER_NAME_COLUMN_INDEX = 5;
    private static final int CALLER_NUMBERTYPE_COLUMN_INDEX = 6;
    private static final int CALLER_NUMBERLABEL_COLUMN_INDEX = 7;
    private static final int PHONE_SUBSCRIPTION_COLUMN_INDEX = 8;

    private static final int PHONE_COLUMN_ID = 0;
    private static final int PHONE_COLUMN_TYPE = 1;
    private static final int PHONE_COLUMN_LABEL = 2;
    private static final int PHONE_COLUMN_NUMBER = 3;
    private static final int PHONE_COLUMN_DISPLAY_NAME = 4;
    private static final int PHONE_COLUMN_CONTACT_ID = 5;

    private static final int EMAIL_COLUMN_ID = 0;
    private static final int EMAIL_COLUMN_DISPLAY_NAME = 1;
    private static final int EMAIL_COLUMN_ADDRESS = 2;

    private static final int QUERY_TOKEN = 42;
    private static final int MODE_MASK_SEARCH = 0x80000000;

    private static final int MODE_DEFAULT_CONTACT = 0;
    private static final int MODE_DEFAULT_PHONE = 1;
    private static final int MODE_DEFAULT_EMAIL = 1 << 1;
    private static final int MODE_DEFAULT_CALL = 1 << 1 << 1;
    private static final int MODE_DEFAULT_SIM = 1 << 1 << 1 << 1;
    private static final int MODE_SEARCH_CONTACT = MODE_DEFAULT_CONTACT | MODE_MASK_SEARCH;
    private static final int MODE_SEARCH_PHONE = MODE_DEFAULT_PHONE | MODE_MASK_SEARCH;
    private static final int MODE_SEARCH_EMAIL = MODE_DEFAULT_EMAIL | MODE_MASK_SEARCH;
    private static final int MODE_SEARCH_CALL = MODE_DEFAULT_CALL | MODE_MASK_SEARCH;
    private static final int MODE_SEARCH_SIM = MODE_DEFAULT_SIM | MODE_MASK_SEARCH;

    private static final int DIALOG_DEL_CALL = 1;
    private ContactItemListAdapter mAdapter;
    private QueryHandler mQueryHandler;
    private Bundle mChoiceSet;
    private Bundle mBackupChoiceSet;
    private EditText mSearchEditor;
    private Button mOKButton;
    private Button mCancelButton;
    private TextView mSelectAllLabel;
    private CheckBox mSelectAllCheckBox;
    private int mMode;
    private boolean mSelectCallLog;
    public static final String KEY_SELECT_CALLLOG = "selectcalllog";

    private ProgressDialog mProgressDialog;
    private SimContactsOperation mSimContactsOperation;
    private Context mContext;
    private Intent mIntent;
    private AccountManager accountManager;

    private static final String[] COLUMN_NAMES = new String[] {
            "name",
            "number",
            "emails",
            "anrs",
            "_id"
    };

    private static final int SIM_COLUMN_DISPLAY_NAME = 0;
    private static final int SIM_COLUMN_NUMBER = 1;
    private static final int SIM_COLUMN_EMAILS = 2;
    private static final int SIM_COLUMN_ANRS = 3;
    private static final int SIM_COLUMN_ID = 4;

    private Drawable mDrawableIncoming;
    private Drawable mDrawableOutgoing;
    private Drawable mDrawableMissed;

    /**
     * control of whether show the contacts in SIM card, if intent has this
     * flag,not show.
     */
    private static final String EXT_NOT_SHOW_SIM_FLAG = "not_sim_show";

    private int MAX_CONTACTS_NUM_TO_SELECT_ONCE = 500;

    //registerReceiver to update content when airplane mode change.
    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO Auto-generated method stub
            if (intent.getAction().equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                updateContent();

                // If now is airplane mode, should cancel import sim contacts
                if (isPickSim() && MoreContactUtils.isAPMOnAndSIMPowerDown(mContext)) {
                    cancelSimContactsImporting();
                }
            }
        }
    };
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        String action = intent.getAction();
        boolean isContact = intent.getBooleanExtra(
                SimContactsConstants.IS_CONTACT, false);
        if (Intent.ACTION_DELETE.equals(action)) {
            mMode = MODE_DEFAULT_CONTACT;
            setTitle(R.string.menu_deleteContact);
        } else if (SimContactsConstants.ACTION_MULTI_PICK.equals(action)) {
            if (!isContact) {
                mMode = MODE_DEFAULT_PHONE;
            } else {
                mMode = MODE_DEFAULT_CONTACT;
            }
        } else if (SimContactsConstants.ACTION_MULTI_PICK_EMAIL.equals(action)) {
            mMode = MODE_DEFAULT_EMAIL;
        } else if (SimContactsConstants.ACTION_MULTI_PICK_CALL.equals(action)) {
            mMode = MODE_DEFAULT_CALL;
            setTitle(R.string.delete_call_title);
            mDrawableIncoming = getResources().getDrawable(
                    R.drawable.ic_call_log_list_incoming_call);
            mDrawableOutgoing = getResources().getDrawable(
                    R.drawable.ic_call_log_list_outgoing_call);
            mDrawableMissed = getResources().getDrawable(
                    R.drawable.ic_call_log_list_missed_call);
            if (intent.getBooleanExtra(KEY_SELECT_CALLLOG, false)) {
                mSelectCallLog = true;
                setTitle(R.string.select_call_title);
            }
        } else if (SimContactsConstants.ACTION_MULTI_PICK_SIM.equals(action)) {
            mMode = MODE_DEFAULT_SIM;
        }

        setContentView(R.layout.pick_contact);
        mChoiceSet = new Bundle();
        mAdapter = new ContactItemListAdapter(this);
        getListView().setAdapter(mAdapter);
        mQueryHandler = new QueryHandler(this);
        mSimContactsOperation = new SimContactsOperation(this);
        mContext = getApplicationContext();
        accountManager = AccountManager.get(mContext);
        initResource();
        startQuery();
        //register receiver.
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        registerReceiver(mBroadcastReceiver, filter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Let the system ignore the menu key when the activity is foreground.
        return false;
    }

    private boolean isSearchMode() {
        return (mMode & MODE_MASK_SEARCH) == MODE_MASK_SEARCH;
    }

    private boolean initSearchText(){
        String s = getIntent().getStringExtra(PeopleActivity.EDITABLE_KEY);
        if (s != null && s.trim().length() > 0) {
            mSearchEditor.setText(s.trim());
            enterSearchMode();
            doFilter(mSearchEditor.getText());
            return true;
        }
        return false;
    }

    private void initResource() {
        mOKButton = (Button) findViewById(R.id.btn_ok);
        mOKButton.setOnClickListener(this);
        mOKButton.setText(getOKString());
        mCancelButton = (Button) findViewById(R.id.btn_cancel);
        mCancelButton.setOnClickListener(this);
        mSearchEditor = ((EditText) findViewById(R.id.search_field));
        mSearchEditor.addTextChangedListener(this);
        mSearchEditor.setOnClickListener(this);
        mSearchEditor.setOnTouchListener(this);
        mSearchEditor.setOnEditorActionListener(this);
        if (isPickCall() || isPickSim()) {
            mSearchEditor.setVisibility(View.INVISIBLE);
        }
        mSelectAllCheckBox = (CheckBox) findViewById(R.id.select_all_check);
        mSelectAllCheckBox.setOnClickListener(this);
        mSelectAllLabel = (TextView) findViewById(R.id.select_all_label);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        hideSoftKeyboard();
        CheckBox checkBox = (CheckBox) v.findViewById(R.id.pick_contact_check);
        boolean isChecked = !checkBox.isChecked();
        checkBox.setChecked(isChecked);
        if (isChecked) {
            String[] value = null;
            ContactItemCache cache = (ContactItemCache) v.getTag();
            if (isPickContact()) {
                value = new String[] {
                        cache.lookupKey, String.valueOf(cache.id)
                };
            } else if (isPickPhone()) {
                value = new String[] {
                        cache.name, cache.number, cache.type,
                        cache.label, cache.contact_id
                };
            } else if (isPickEmail()) {
                value = new String[] {
                        cache.name, cache.email
                };
            } else if (isPickSim()) {
                value = new String[] {
                        cache.name, cache.number, cache.email, cache.anrs
                };
            } else if (isPickCall()) {
                if (mSelectCallLog) {
                    value = new String[] {
                            cache.name, cache.number
                    };
                }
            }
            mChoiceSet.putStringArray(String.valueOf(id), value);
            if (!isSearchMode()) {
                if (mChoiceSet.size() == mAdapter.getCount()) {
                    mSelectAllCheckBox.setChecked(true);
                }
            }
        } else {
            mChoiceSet.remove(String.valueOf(id));
            mSelectAllCheckBox.setChecked(false);
        }
        mOKButton.setText(getOKString());
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK: {
                if (isSearchMode()) {
                    exitSearchMode(false);
                    return true;
                }
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private String getOKString() {
        if (0 == mChoiceSet.size()) {
            mOKButton.setEnabled(false);
        } else {
            mOKButton.setEnabled(true);
        }

        return getString(R.string.btn_ok) + "(" + mChoiceSet.size() + ")";
    }

    private void backupChoiceSet() {
        mBackupChoiceSet = (Bundle) mChoiceSet.clone();
    }

    private void restoreChoiceSet() {
        mChoiceSet = mBackupChoiceSet;
    }

    private void enterSearchMode() {
        mMode |= MODE_MASK_SEARCH;
        mSelectAllLabel.setVisibility(View.GONE);
        mSelectAllCheckBox.setVisibility(View.GONE);
        backupChoiceSet();
    }

    private void exitSearchMode(boolean isConfirmed) {
        mMode &= ~MODE_MASK_SEARCH;
        hideSoftKeyboard();
        mSelectAllLabel.setVisibility(View.VISIBLE);
        mSelectAllCheckBox.setVisibility(View.VISIBLE);
        if (!isConfirmed) {
            restoreChoiceSet();
        }
        mSearchEditor.setText("");
        mOKButton.setText(getOKString());
    }

    protected Dialog onCreateDialog(int id, Bundle bundle) {
        switch (id) {
            case R.id.dialog_delete_contact_confirmation: {
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.deleteConfirmation_title)
                        .setIconAttribute(android.R.attr.alertDialogIcon)
                        .setMessage(R.string.ContactMultiDeleteConfirmation)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok,
                                new DeleteClickListener()).create();
            }
            case DIALOG_DEL_CALL: {
                return new AlertDialog.Builder(this).setTitle(R.string.title_del_call)
                        .setIcon(android.R.drawable.ic_dialog_alert).setMessage(
                                R.string.delete_call_alert).setNegativeButton(
                                android.R.string.cancel, null).setPositiveButton(
                                android.R.string.ok, new DeleteClickListener()).create();
            }
            case R.id.dialog_import_sim_contact_confirmation: {
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.importConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.ContactMultiImportConfirmation)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok,
                                new DeleteClickListener()).create();
            }

        }

        return super.onCreateDialog(id, bundle);
    }

    private class DeleteContactsThread extends Thread
            implements OnCancelListener, DialogInterface.OnClickListener {

        boolean mCanceled = false;
        private String name = null;
        private String number = null;
        final String[] PROJECTION = new String[] {
                Phone.CONTACT_ID,
                Phone.NUMBER,
                Phone.DISPLAY_NAME
        };
        private final int COLUMN_NUMBER = 1;
        private final int COLUMN_NAME = 2;

        private ArrayList<ContentProviderOperation> mOpsCalls = null;

        private ArrayList<ContentProviderOperation> mOpsContacts = null;

        public DeleteContactsThread() {
        }

        @Override
        public void run() {
            final ContentResolver resolver = getContentResolver();

            // The mChoiceSet object will change when activity restart, but
            // DeleteContactsThread running in background, so we need clone the
            // choiceSet to avoid ConcurrentModificationException.
            Bundle choiceSet = (Bundle) mChoiceSet.clone();
            Set<String> keySet = choiceSet.keySet();
            Iterator<String> it = keySet.iterator();

            android.content.ContentProviderOperation.Builder builder = null;

            ContentProviderOperation cpo = null;

            // Current contact count we can delete.
            int count = 0;

            // The contacts we batch delete once.
            final int BATCH_DELETE_CONTACT_NUMBER = 100;

            mOpsCalls = new ArrayList<ContentProviderOperation>();
            mOpsContacts = new ArrayList<ContentProviderOperation>();

            while (!mCanceled && it.hasNext()) {
                String id = it.next();
                Uri uri = null;
                if (isPickCall()) {
                    uri = Uri.withAppendedPath(Calls.CONTENT_URI, id);
                    builder = ContentProviderOperation.newDelete(uri);
                    cpo = builder.build();
                    mOpsCalls.add(cpo);
                } else {
                    uri = Uri.withAppendedPath(Contacts.CONTENT_URI, id);
                    long longId = Long.parseLong(id);
                    int subscription =
                            mSimContactsOperation.getSimSubscription(longId);

                    if (subscription == SimContactsConstants.SUB_1
                            || subscription == SimContactsConstants.SUB_2) {
                        if (MoreContactUtils.isAPMOnAndSIMPowerDown(mContext)) {
                            break;
                        }
                        ContentValues values =
                                mSimContactsOperation.getSimAccountValues(longId);
                        log("values is : " + values + "; sub is " + subscription);
                        int result = mSimContactsOperation.delete(values, subscription);
                        if (result == 0) {
                            mProgressDialog.incrementProgressBy(1);
                            continue;
                        }
                    }
                    builder = ContentProviderOperation.newDelete(uri);
                    cpo = builder.build();
                    mOpsContacts.add(cpo);
                }
                // If contacts more than 2000, delete all contacts
                // one by one will cause UI nonresponse.
                mProgressDialog.incrementProgressBy(1);
                // We batch delete contacts every 100.
                if (count % BATCH_DELETE_CONTACT_NUMBER == 0) {
                    batchDelete();
                }
                count ++;
            }

            batchDelete();
            mOpsCalls = null;
            mOpsContacts = null;
            Log.d(TAG, "DeleteContactsThread run, progress:" + mProgressDialog.getProgress());
            mProgressDialog.dismiss();
            finish();
        }

        /**
         * Batch delete contacts more efficient than one by one.
         */
        private void batchDelete() {
            try {
                 mContext.getContentResolver().applyBatch(CallLog.AUTHORITY, mOpsCalls);
                 mContext.getContentResolver().applyBatch(ContactsContract.AUTHORITY, mOpsContacts);
                 mOpsCalls.clear();
                 mOpsContacts.clear();
             } catch (RemoteException e) {
                 e.printStackTrace();
             } catch (OperationApplicationException e) {
                 e.printStackTrace();
             }
        }

        public void onCancel(DialogInterface dialog) {
            mCanceled = true;
            Log.d(TAG, "DeleteContactsThread onCancel, progress:" + mProgressDialog.getProgress());
            //  Give a toast show to tell user delete termination
        }

        public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_NEGATIVE) {
                mCanceled = true;
                mProgressDialog.dismiss();
            }
        }

    }

    private class DeleteClickListener implements DialogInterface.OnClickListener {
        public void onClick(DialogInterface dialog, int which) {
            CharSequence title = null;
            CharSequence message = null;

            if (isPickCall()) {
                title = getString(R.string.delete_call_title);
                message = getString(R.string.delete_call_message);
            } else if (isPickSim()) {
                title = getString(R.string.import_sim_contacts_title);
                message = getString(R.string.import_sim_contacts_message);
            } else {
                title = getString(R.string.delete_contacts_title);
                message = getString(R.string.delete_contacts_message);
            }

            Thread thread;
            if (isPickSim()) {
                thread = new ImportAllSimContactsThread();
            } else {
                thread = new DeleteContactsThread();
            }

            DialogInterface.OnKeyListener keyListener = new DialogInterface.OnKeyListener() {
                public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                    switch (keyCode) {
                        case KeyEvent.KEYCODE_SEARCH:
                        case KeyEvent.KEYCODE_CALL:
                            return true;
                        default:
                            return false;
                    }
                }
            };

            mProgressDialog = new ProgressDialog(MultiPickContactActivity.this);
            mProgressDialog.setTitle(title);
            mProgressDialog.setMessage(message);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                    getString(R.string.btn_cancel), (OnClickListener) thread);
            mProgressDialog.setOnCancelListener((OnCancelListener) thread);
            mProgressDialog.setOnKeyListener(keyListener);
            mProgressDialog.setProgress(0);
            mProgressDialog.setMax(mChoiceSet.size());

            // set dialog can not be canceled by touching outside area of
            // dialog.
            mProgressDialog.setCanceledOnTouchOutside(false);
            mProgressDialog.show();

            thread.start();
        }
    }

    public void onClick(View v) {
        int id = v.getId();
        switch (id) {
            case R.id.btn_ok:
                if (isSearchMode()) {
                    exitSearchMode(true);
                }
                if (mMode == MODE_DEFAULT_CONTACT) {
                    if (SimContactsConstants.ACTION_MULTI_PICK.equals(getIntent().getAction())) {
                        if (mChoiceSet.size() > MAX_CONTACTS_NUM_TO_SELECT_ONCE) {
                            Toast.makeText(
                                    mContext,
                                    mContext.getString(R.string.too_many_contacts_add_to_group,
                                            MAX_CONTACTS_NUM_TO_SELECT_ONCE), Toast.LENGTH_SHORT)
                                    .show();
                        } else {
                            Intent intent = new Intent();
                            Bundle bundle = new Bundle();
                            bundle.putBundle(SimContactsConstants.RESULT_KEY, mChoiceSet);
                            intent.putExtras(bundle);
                            this.setResult(RESULT_OK, intent);
                            finish();
                        }
                    } else if (mChoiceSet.size() > 0) {
                        showDialog(R.id.dialog_delete_contact_confirmation);
                    }
                } else if (mMode == MODE_DEFAULT_PHONE) {
                    Intent intent = new Intent();
                    Bundle bundle = new Bundle();
                    bundle.putBundle(SimContactsConstants.RESULT_KEY, mChoiceSet);
                    intent.putExtras(bundle);
                    this.setResult(RESULT_OK, intent);
                    finish();
                } else if (mMode == MODE_DEFAULT_SIM) {
                    if (mChoiceSet.size() > 0) {
                        showDialog(R.id.dialog_import_sim_contact_confirmation);
                    }
                } else if (mMode == MODE_DEFAULT_EMAIL) {
                    Intent intent = new Intent();
                    Bundle bundle = new Bundle();
                    bundle.putBundle(SimContactsConstants.RESULT_KEY, mChoiceSet);
                    intent.putExtras(bundle);
                    this.setResult(RESULT_OK, intent);
                    finish();
                } else if (mMode == MODE_DEFAULT_CALL) {
                    if (mChoiceSet.size() > 0) {
                        if (mSelectCallLog) {
                            Intent intent = new Intent();
                            Bundle bundle = new Bundle();
                            bundle.putBundle(SimContactsConstants.RESULT_KEY, mChoiceSet);
                            intent.putExtras(bundle);
                            this.setResult(RESULT_OK, intent);
                            finish();
                        } else {
                            showDialog(DIALOG_DEL_CALL);
                        }
                    }
                }
                break;
            case R.id.btn_cancel:
                if (!isSearchMode()) {
                    this.setResult(this.RESULT_CANCELED);
                    finish();
                } else {
                    exitSearchMode(false);
                }
                break;
            case R.id.select_all_check:
                if (mSelectAllCheckBox.isChecked()) {
                    selectAll(true);
                } else {
                    selectAll(false);
                }
                break;
            case R.id.search_field:
                enterSearchMode();
                break;
        }
    }

    @Override
    public void onDestroy() {
        mQueryHandler.removeCallbacksAndMessages(QUERY_TOKEN);
        if (mAdapter.getCursor() != null) {
            mAdapter.getCursor().close();
        }

        if (mProgressDialog != null) {
            mProgressDialog.cancel();
        }

        //unregister receiver.
        if (mBroadcastReceiver != null) {
            unregisterReceiver(mBroadcastReceiver);
        }

        super.onDestroy();
    }

    private Uri getUriToQuery() {
        Uri uri;
        switch (mMode) {
            case MODE_DEFAULT_CONTACT:
            case MODE_SEARCH_CONTACT:
                uri = Contacts.CONTENT_URI;
                break;
            case MODE_DEFAULT_EMAIL:
            case MODE_SEARCH_EMAIL:
                uri = Email.CONTENT_URI;
                break;
            case MODE_DEFAULT_PHONE:
            case MODE_SEARCH_PHONE:
                uri = Phone.CONTENT_URI;
                break;
            case MODE_DEFAULT_CALL:
            case MODE_SEARCH_CALL:
                uri = Calls.CONTENT_URI_WITH_VOICEMAIL;
                break;
            case MODE_DEFAULT_SIM:
            case MODE_SEARCH_SIM: {
                mIntent = getIntent();
                int subscription = mIntent.getIntExtra(SimContactsConstants.SUB, 0);
                uri = querySimContacts(subscription);
                break;
            }
            default:
                throw new IllegalArgumentException("getUriToQuery: Incorrect mode: " + mMode);
        }
        return uri.buildUpon()
                .appendQueryParameter(Contacts.EXTRA_ADDRESS_BOOK_INDEX,
                        "true")
                .build();
    }

    /**
     * Just get the uri we need to query contacts.
     *
     * @return uri with account info parameter if explicit request contacts fit
     *         current account, else just search contacts fit specified keyword.
     */
    private Uri getContactsFilterUri() {
        Uri filterUri = Contacts.CONTENT_FILTER_URI;

        // To confirm if the search rule must contain account limitation.
        Intent intent = getIntent();
        ContactListFilter filter = (ContactListFilter) intent.getParcelableExtra(
                AccountFilterActivity.KEY_EXTRA_CONTACT_LIST_FILTER);

        if (filter != null &&
                filter.filterType == ContactListFilter.FILTER_TYPE_ACCOUNT) {

            // Need consider account info limitation, construct the uri with
            // account info query parameter.
            Builder builder = filterUri.buildUpon();
            filter.addAccountQueryParameterToUrl(builder);
            return builder.build();
        }

        if (!isShowSIM()) {
            filterUri = filterUri.buildUpon().appendQueryParameter(RawContacts.ACCOUNT_TYPE,
                     SimAccountType.ACCOUNT_TYPE)
                    .appendQueryParameter(SimContactsConstants.WITHOUT_SIM_FLAG,
                     "true").build();
        }
        // No need to consider account info limitation, just return a uri
        // with "filter" path.
        return filterUri;
    }
    private Uri getFilterUri() {
        switch (mMode) {
            case MODE_SEARCH_CONTACT:
                return getContactsFilterUri();
            case MODE_SEARCH_PHONE:
                return Phone.CONTENT_FILTER_URI;
            case MODE_SEARCH_EMAIL:
                return Email.CONTENT_FILTER_URI;
            default:
                log("getFilterUri: Incorrect mode: " + mMode);
        }
        return Contacts.CONTENT_FILTER_URI;
    }

    public String[] getProjectionForQuery() {
        switch (mMode) {
            case MODE_DEFAULT_CONTACT:
            case MODE_SEARCH_CONTACT:
                return CONTACTS_SUMMARY_PROJECTION;
            case MODE_DEFAULT_PHONE:
            case MODE_SEARCH_PHONE:
                return PHONES_PROJECTION;
            case MODE_DEFAULT_EMAIL:
            case MODE_SEARCH_EMAIL:
                return EMAILS_PROJECTION;
            case MODE_DEFAULT_CALL:
            case MODE_SEARCH_CALL:
                return CALL_LOG_PROJECTION;
            case MODE_DEFAULT_SIM:
            case MODE_SEARCH_SIM:
                return COLUMN_NAMES;
            default:
                log("getProjectionForQuery: Incorrect mode: " + mMode);
        }
        return CONTACTS_SUMMARY_PROJECTION;
    }

    private String getSortOrder(String[] projection) {
        switch (mMode) {
            case MODE_DEFAULT_CALL:
            case MODE_SEARCH_CALL:
                return CALL_LOG_PROJECTION[2] + SORT_ORDER;
        }
        return RawContacts.SORT_KEY_PRIMARY;
    }

    private String getSelectionForQuery() {
        switch (mMode) {
            case MODE_DEFAULT_EMAIL:
            case MODE_SEARCH_EMAIL:
            case MODE_DEFAULT_PHONE:
            case MODE_SEARCH_PHONE:
                if (isShowSIM()) {
                    return null;
                } else {
                    return PHONES_SELECTION;
                }
            case MODE_DEFAULT_CONTACT:
                return getSelectionForAccount();
            case MODE_DEFAULT_SIM:
            case MODE_SEARCH_SIM:
                return null;
            case MODE_DEFAULT_CALL:
                // Add a subscription judgement, if selection = -1 that means
                // need query both cards.
                String selection = null;
                int slot = getIntent().getIntExtra(SimContactsConstants.SUB,
                    SimContactsConstants.SUB_INVALID);
                int[] subIds = SubscriptionManager.getSubId(slot);
                if (SimContactsConstants.SUB_INVALID != slot
                        && subIds != null && subIds.length > 0) {
                    selection = Calls.PHONE_ACCOUNT_ID + "=" + Long.toString(subIds[0]);
                }
                return selection;
            default:
                return null;
        }
    }

    private String getSelectionForAccount() {
        @SuppressWarnings("deprecation")
        ContactListFilter filter = (ContactListFilter) getIntent().getExtra(
                AccountFilterActivity.KEY_EXTRA_CONTACT_LIST_FILTER);
        if (filter == null) {
            return null;
        }
        switch (filter.filterType) {
            case ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS:
                return null;
            case ContactListFilter.FILTER_TYPE_CUSTOM:
                return CONTACTS_SELECTION;
            case ContactListFilter.FILTER_TYPE_ACCOUNT:
                return null;
        }
        return null;
    }

    private String[] getSelectionArgsForQuery() {
        switch (mMode) {
            case MODE_DEFAULT_EMAIL:
            case MODE_SEARCH_EMAIL:
            case MODE_DEFAULT_PHONE:
            case MODE_SEARCH_PHONE:
                if (isShowSIM()) {
                    return null;
                } else {
                    return PHONES_SELECTION_ARGS;
                }
            case MODE_DEFAULT_SIM:
            case MODE_SEARCH_SIM:
                return null;
            default:
                return null;
        }
    }

    private boolean isShowSIM() {
        // if airplane mode on, do not show SIM.
        return !getIntent().hasExtra(EXT_NOT_SHOW_SIM_FLAG)
                && !MoreContactUtils.isAPMOnAndSIMPowerDown(mContext);
    }

    public void startQuery() {
        Uri uri = getUriToQuery();
        ContactListFilter filter = (ContactListFilter) getIntent().getExtra(
                          AccountFilterActivity.KEY_EXTRA_CONTACT_LIST_FILTER);
        if (filter != null) {
            if (filter.filterType == ContactListFilter.FILTER_TYPE_ACCOUNT) {
                // We should exclude the invisiable contacts.
                uri = uri.buildUpon().appendQueryParameter(RawContacts.ACCOUNT_NAME,
                         filter.accountName).appendQueryParameter(RawContacts.ACCOUNT_TYPE,
                         filter.accountType)
                        .appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
                         ContactsContract.Directory.DEFAULT+"").build();
            } else if (filter.filterType == ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS) {
                // Do not query sim contacts in airplane mode.
                if (!isShowSIM()) {
                    uri = uri.buildUpon().appendQueryParameter(RawContacts.ACCOUNT_TYPE,
                              SimAccountType.ACCOUNT_TYPE)
                             .appendQueryParameter(SimContactsConstants.WITHOUT_SIM_FLAG,
                              "true").build();
                }
            }
        }
        String[] projection = getProjectionForQuery();
        String selection = getSelectionForQuery();
        String[] selectionArgs = getSelectionArgsForQuery();
        mQueryHandler.startQuery(QUERY_TOKEN, null, uri, projection, selection,
                selectionArgs, getSortOrder(projection));
    }

    @Override
    public void afterTextChanged(Editable s) {
        if (!TextUtils.isEmpty(s)) {
            if (!isSearchMode()) {
                enterSearchMode();
            }
        } else if (isSearchMode()) {
            exitSearchMode(true);
        }
        doFilter(s);
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    public void doFilter(Editable s) {
        if (TextUtils.isEmpty(s)) {
            startQuery();
            return;
        }

        Uri uri = Uri.withAppendedPath(getFilterUri(), Uri.encode(s.toString()));
        String[] projection = getProjectionForQuery();
        String selection = getSelectionForQuery();
        String[] selectionArgs = getSelectionArgsForQuery();
        mQueryHandler.startQuery(QUERY_TOKEN, null, uri, projection, selection,
                selectionArgs, getSortOrder(projection));
    }

    public void updateContent() {
        if (isSearchMode()) {
            doFilter(mSearchEditor.getText());
        } else {
            startQuery();
        }
    }

    private CharSequence getDisplayNumber(CharSequence number) {
        if (TextUtils.isEmpty(number)) {
            return "";
        }
        if (PhoneNumberUtils.isVoiceMailNumber(number.toString())) {
            return getString(R.string.voicemail);
        }
        return number;
    }

    private boolean isPickContact() {
        return mMode == MODE_DEFAULT_CONTACT || mMode == MODE_SEARCH_CONTACT;
    }

    private boolean isPickPhone() {
        return mMode == MODE_DEFAULT_PHONE || mMode == MODE_SEARCH_PHONE;
    }

    private boolean isPickSim() {
        return mMode == MODE_DEFAULT_SIM || mMode == MODE_SEARCH_SIM;
    }

    private boolean isPickEmail() {
        return mMode == MODE_DEFAULT_EMAIL || mMode == MODE_SEARCH_EMAIL;
    }

    private boolean isPickCall() {
        return mMode == MODE_DEFAULT_CALL || mMode == MODE_SEARCH_CALL;
    }

    private void selectAll(boolean isSelected) {
        // update mChoiceSet.
        // TODO: make it more efficient
        Cursor cursor = mAdapter.getCursor();
        if (cursor == null) {
            log("cursor is null.");
            return;
        }

        cursor.moveToPosition(-1);
        while (cursor.moveToNext()) {
            String id = null;
            String[] value = null;
            if (isPickContact()) {
                id = String.valueOf(cursor.getLong(SUMMARY_ID_COLUMN_INDEX));
                value = new String[] {
                        cursor.getString(SUMMARY_LOOKUP_KEY_COLUMN_INDEX), id
                };
            } else if (isPickPhone()) {
                id = String.valueOf(cursor.getLong(PHONE_COLUMN_ID));
                String name = cursor.getString(PHONE_COLUMN_DISPLAY_NAME);
                String number = cursor.getString(PHONE_COLUMN_NUMBER);
                String type = String.valueOf(cursor.getInt(PHONE_COLUMN_TYPE));
                String label = cursor.getString(PHONE_COLUMN_LABEL);
                String contact_id = String.valueOf(cursor.getLong(PHONE_COLUMN_CONTACT_ID));
                value = new String[] {
                        name, number, type, label, contact_id
                };
            } else if (isPickEmail()) {
                id = String.valueOf(cursor.getLong(EMAIL_COLUMN_ID));
                String name = cursor.getString(EMAIL_COLUMN_DISPLAY_NAME);
                String email = cursor.getString(EMAIL_COLUMN_ADDRESS);
                value = new String[] {
                        name, email, id
                };
            } else if (isPickCall()) {
                if (mSelectCallLog) {
                    id = String.valueOf(cursor.getLong(ID_COLUMN_INDEX));
                    String number = cursor.getString(NUMBER_COLUMN_INDEX);
                    String name = cursor.getString(CALLER_NAME_COLUMN_INDEX);
                    value = new String[] {
                            name, number
                    };
                } else {
                    id = String.valueOf(cursor.getLong(ID_COLUMN_INDEX));
                    value = new String[] {
                            id
                    };
                }
            } else if (isPickSim()) {
                id = String.valueOf(cursor.getLong(SIM_COLUMN_ID));
                String name = cursor.getString(SIM_COLUMN_DISPLAY_NAME);
                String number = cursor.getString(SIM_COLUMN_NUMBER);
                String email = cursor.getString(SIM_COLUMN_EMAILS);
                String anrs = cursor.getString(SIM_COLUMN_ANRS);
                value = new String[] {
                        name, number, email, anrs
                };
            }
            if (isSelected) {
                mChoiceSet.putStringArray(id, value);
            } else {
                mChoiceSet.remove(id);
            }
        }

        // update UI items.
        mOKButton.setText(getOKString());

        int count = mList.getChildCount();
        for (int i = 0; i < count; i++) {
            View v = mList.getChildAt(i);
            CheckBox checkBox = (CheckBox) v.findViewById(R.id.pick_contact_check);
            checkBox.setChecked(isSelected);
        }
    }

    private class QueryHandler extends AsyncQueryHandler {
        protected WeakReference<MultiPickContactActivity> mActivity;

        public QueryHandler(Context context) {
            super(context.getContentResolver());
            mActivity = new WeakReference<MultiPickContactActivity>(
                    (MultiPickContactActivity) context);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            // In the case of low memory, the WeakReference object may be
            // recycled.
            if (mActivity == null || mActivity.get() == null) {
                mActivity = new WeakReference<MultiPickContactActivity>(
                        MultiPickContactActivity.this);
            }
            final MultiPickContactActivity activity = mActivity.get();
            activity.mAdapter.changeCursor(cursor);
            if (cursor == null || cursor.getCount() == 0) {
                Toast.makeText(mContext, R.string.listFoundAllContactsZero,
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private final class ContactItemCache {
        long id;
        String name;
        String number;
        String lookupKey;
        String type;
        String label;
        String contact_id;
        String email;
        String anrs;
    }

    private final class ContactItemListAdapter extends CursorAdapter {
        Context mContext;
        protected LayoutInflater mInflater;
        private ContactPhotoManager mContactPhotoManager;

        public ContactItemListAdapter(Context context) {
            super(context, null, false);

            mContext = context;
            mInflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mContactPhotoManager = ContactPhotoManager.getInstance(mContext);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            ContactItemCache cache = (ContactItemCache) view.getTag();
            if (isPickContact()) {
                cache.id = cursor.getLong(SUMMARY_ID_COLUMN_INDEX);
                cache.lookupKey = cursor.getString(SUMMARY_LOOKUP_KEY_COLUMN_INDEX);
                cache.name = cursor.getString(SUMMARY_DISPLAY_NAME_PRIMARY_COLUMN_INDEX);
                ((TextView) view.findViewById(R.id.pick_contact_name))
                        .setText(cache.name == null ? "" : cache.name);
                view.findViewById(R.id.pick_contact_number).setVisibility(View.GONE);

                long photoId = 0;
                if (!cursor.isNull(SUMMARY_PHOTO_ID_COLUMN_INDEX)) {
                    photoId = cursor.getLong(SUMMARY_PHOTO_ID_COLUMN_INDEX);
                }
                Account account = null;
                if (!cursor.isNull(SUMMARY_ACCOUNT_TYPE) && !cursor.isNull(SUMMARY_ACCOUNT_NAME)) {
                    final String accountType = cursor.getString(SUMMARY_ACCOUNT_TYPE);
                    final String accountName = cursor.getString(SUMMARY_ACCOUNT_NAME);
                    account = new Account(accountName, accountType);
                }
                ImageView photo = ((ImageView) view.findViewById(R.id.pick_contact_photo));
                photo.setVisibility(View.VISIBLE);
                DefaultImageRequest request = null;
                if (photoId == 0) {
                    request = new DefaultImageRequest(cache.name, cache.lookupKey, true);
                }
                mContactPhotoManager.loadThumbnail(photo, photoId, account, false, true, request);
            } else if (isPickPhone()) {
                cache.id = cursor.getLong(PHONE_COLUMN_ID);
                cache.name = cursor.getString(PHONE_COLUMN_DISPLAY_NAME);
                cache.number = cursor.getString(PHONE_COLUMN_NUMBER);
                cache.label = cursor.getString(PHONE_COLUMN_LABEL);
                cache.type = String.valueOf(cursor.getInt(PHONE_COLUMN_TYPE));
                ((TextView) view.findViewById(R.id.pick_contact_name)).setText(cache.name);
                ((TextView) view.findViewById(R.id.pick_contact_number)).setText(cache.number);
            } else if (isPickSim()) {
                cache.id = cursor.getLong(SIM_COLUMN_ID);
                cache.name = cursor.getString(SIM_COLUMN_DISPLAY_NAME);
                cache.number = cursor.getString(SIM_COLUMN_NUMBER);
                cache.email = cursor.getString(SIM_COLUMN_EMAILS);
                cache.anrs = cursor.getString(SIM_COLUMN_ANRS);
                ((TextView) view.findViewById(R.id.pick_contact_name)).setText(cache.name);
                if (!TextUtils.isEmpty(cache.number)) {
                    ((TextView) view.findViewById(R.id.pick_contact_number)).setText(cache.number);
                } else if (!TextUtils.isEmpty(cache.email)) {
                    String[] emailArray = (cache.email).split(",");
                    ((TextView) view.findViewById(R.id.pick_contact_number)).setText(emailArray[0]);
                }
            } else if (isPickEmail()) {
                cache.id = cursor.getLong(EMAIL_COLUMN_ID);
                cache.name = cursor.getString(EMAIL_COLUMN_DISPLAY_NAME);
                cache.email = cursor.getString(EMAIL_COLUMN_ADDRESS);
                ((TextView) view.findViewById(R.id.pick_contact_name)).setText(cache.name);
                ((TextView) view.findViewById(R.id.pick_contact_number)).setText(cache.email);
            }  else if (isPickCall()) {
                cache.id = cursor.getLong(ID_COLUMN_INDEX);
                cache.name = cursor.getString(CALLER_NAME_COLUMN_INDEX);
                cache.number = cursor.getString(NUMBER_COLUMN_INDEX);
                String number = cursor.getString(NUMBER_COLUMN_INDEX);
                String callerName = cursor.getString(CALLER_NAME_COLUMN_INDEX);
                int callerNumberType = cursor.getInt(CALLER_NUMBERTYPE_COLUMN_INDEX);
                String callerNumberLabel = cursor.getString(CALLER_NUMBERLABEL_COLUMN_INDEX);
                String subId = cursor.getString(PHONE_SUBSCRIPTION_COLUMN_INDEX);
                long date = cursor.getLong(DATE_COLUMN_INDEX);
                long duration = cursor.getLong(DURATION_COLUMN_INDEX);
                int type = cursor.getInt(CALL_TYPE_COLUMN_INDEX);

                ImageView callType = (ImageView) view.findViewById(R.id.call_type_icon);
                TextView dateText = (TextView) view.findViewById(R.id.date);
                TextView durationText = (TextView) view.findViewById(R.id.duration);
                TextView subSlotText = (TextView) view.findViewById(R.id.slot);
                TextView numberLableText = (TextView) view.findViewById(R.id.label);
                TextView numberText = (TextView) view.findViewById(R.id.number);
                TextView callerNameText = (TextView) view.findViewById(R.id.line1);

                // only for monkey test, callType can not be null in normal behaviour
                if(callType == null){
                    return;
                }

                callType.setVisibility(View.VISIBLE);
                // Set the icon
                switch (type) {
                    case Calls.INCOMING_TYPE:
                        callType.setImageDrawable(mDrawableIncoming);
                        break;
                    case Calls.OUTGOING_TYPE:
                        callType.setImageDrawable(mDrawableOutgoing);
                        break;
                    case Calls.MISSED_TYPE:
                        callType.setImageDrawable(mDrawableMissed);
                        break;
                    default:
                        callType.setVisibility(View.INVISIBLE);
                        break;
                }

                // set the number
                if (!TextUtils.isEmpty(callerName)) {
                    callerNameText.setText(callerName);
                    callerNameText.setVisibility(View.VISIBLE);
                    numberText.setVisibility(View.GONE);
                    numberText.setText(null);
                } else {
                    callerNameText.setVisibility(View.GONE);
                    callerNameText.setText(null);
                    numberText.setVisibility(View.VISIBLE);
                    numberText.setText(getDisplayNumber(number));
                }

                CharSequence numberLabel = null;
                if (!PhoneNumberUtils.isUriNumber(number)) {
                    numberLabel = Phone.getTypeLabel(context.getResources(),
                            callerNumberType, callerNumberLabel);
                }
                if (!TextUtils.isEmpty(numberLabel)) {
                    numberLableText.setText(numberLabel);
                    numberLableText.setVisibility(View.VISIBLE);
                } else {
                    numberLableText.setText(null);
                    numberLableText.setVisibility(View.INVISIBLE);
                }

                // set date
                dateText.setText(DateUtils.getRelativeTimeSpanString(date,
                        System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE));

                // set duration
                durationText.setText(DateUtils.formatElapsedTime(duration));

                int slot = SimContactsConstants.SUB_INVALID;
                if (subId != null && !subId.equals("E")) {
                    slot = SubscriptionManager.getSlotId(Integer.parseInt(subId));
                }
                // set slot
                subSlotText.setText(MoreContactUtils.getMultiSimAliasesName(
                        MultiPickContactActivity.this, slot));
            }

            CheckBox checkBox = (CheckBox) view.findViewById(R.id.pick_contact_check);
            if (mChoiceSet.containsKey(String.valueOf(cache.id))) {
                checkBox.setChecked(true);
            } else {
                checkBox.setChecked(false);
            }
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            View v = null;
            if (isPickCall()) {
                v = mInflater.inflate(R.layout.pick_calls_item, parent, false);
            } else {
                v = mInflater
                        .inflate(R.layout.pick_contact_item, parent, false);
            }
            ContactItemCache cache = new ContactItemCache();
            v.setTag(cache);
            return v;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            View v;

            if (!mCursor.moveToPosition(position)) {
                throw new IllegalStateException(
                        "couldn't move cursor to position " + position);
            }
            if (convertView != null && convertView.getTag() != null) {
                v = convertView;
            } else {
                v = newView(mContext, mCursor, parent);
            }
            bindView(v, mContext, mCursor);
            return v;
        }

        @Override
        protected void onContentChanged() {
            updateContent();
        }

        @Override
        public void changeCursor(Cursor cursor) {
            super.changeCursor(cursor);
            if (!isSearchMode()) {
                if (cursor == null || cursor.getCount() == 0) {
                    mSelectAllCheckBox.setChecked(false);
                    mSelectAllLabel.setEnabled(false);
                    mSelectAllCheckBox.setClickable(false);
                } else {
                    mSelectAllLabel.setEnabled(true);
                    mSelectAllCheckBox.setClickable(true);
                    if (cursor.getCount() > mChoiceSet.size()) {
                        mSelectAllCheckBox.setChecked(false);
                    } else {
                        mSelectAllCheckBox.setChecked(true);
                    }
                }
            }
        }
    }

    /**
     * Dismisses the soft keyboard when the list takes focus.
     */
    public boolean onTouch(View view, MotionEvent event) {
        if (view == getListView()) {
            hideSoftKeyboard();
        }
        return false;
    }

    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            hideSoftKeyboard();
            return true;
        }
        return false;
    }

    private void hideSoftKeyboard() {
        // Hide soft keyboard, if visible
        InputMethodManager inputMethodManager = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(mSearchEditor.getWindowToken(), 0);
    }

    private String getTextFilter() {
        if (mSearchEditor != null) {
            return mSearchEditor.getText().toString();
        }
        return null;
    }

    protected static void log(String msg) {
        if (DEBUG)
            Log.d(TAG, msg);
    }

    private Uri querySimContacts(int subscription) {
        Uri uri = null;
        if (subscription != SimContactsConstants.SUB_1
                && subscription != SimContactsConstants.SUB_2) {
            return uri;
        }
        int[] subId = SubscriptionManager.getSubId(subscription);
        if (subId != null && TelephonyManager.getDefault().isMultiSimEnabled()) {
            uri = Uri.parse(SimContactsConstants.SIM_SUB_URI + subId[0]);
        }
        else {
            uri = Uri.parse(SimContactsConstants.SIM_URI);
        }

        return uri;
    }

    protected Account[] getSimAccounts() {
        return accountManager
                .getAccountsByType(SimContactsConstants.ACCOUNT_TYPE_SIM);
    }

    private class ImportAllSimContactsThread extends Thread
            implements OnCancelListener, DialogInterface.OnClickListener {
        boolean mCanceled = false;
        // The total count how many to import.
        private int mTotalCount = 0;
        // The real count have imported.
        private int mActualCount = 0;

        private Account mAccount;

        public ImportAllSimContactsThread() {
        }

        @Override
        public void run() {
            final ContentValues emptyContentValues = new ContentValues();
            final ContentResolver resolver = mContext.getContentResolver();

            String type = getIntent().getStringExtra(SimContactsConstants.ACCOUNT_TYPE);
            String name = getIntent().getStringExtra(SimContactsConstants.ACCOUNT_NAME);
            mAccount = new Account(name != null ? name : SimContactsConstants.PHONE_NAME,
                    type != null ? type
                            : SimContactsConstants.ACCOUNT_TYPE_PHONE);
            log("import sim contact to account: " + mAccount);
            Set<String> keySet = mChoiceSet.keySet();
            mTotalCount = keySet.size();
            Iterator<String> it = keySet.iterator();
            while (!mCanceled && it.hasNext()) {
                String key = it.next();
                String[] values = mChoiceSet.getStringArray(key);
                actuallyImportOneSimContact(values, resolver, mAccount);
                mActualCount++;
                mProgressDialog.incrementProgressBy(1);
            }
            finish();
        }

        public void onCancel(DialogInterface dialog) {
            mCanceled = true;
            // Give a toast show to tell user import termination.
            if (mActualCount < mTotalCount) {
                Toast.makeText(mContext, R.string.import_stop, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(mContext, R.string.import_finish, Toast.LENGTH_SHORT)
                        .show();
            }
        }

        public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_NEGATIVE) {
                mCanceled = true;
                mProgressDialog.dismiss();
            }
        }
    }

    private static void actuallyImportOneSimContact(
            String[] values, final ContentResolver resolver, Account account) {

        final String name = values[SIM_COLUMN_DISPLAY_NAME];
        final String phoneNumber = values[SIM_COLUMN_NUMBER];
        final String emailAddresses = values[SIM_COLUMN_EMAILS];
        final String anrs = values[SIM_COLUMN_ANRS];
        final String[] emailAddressArray;
        final String[] anrArray;
        if (!TextUtils.isEmpty(emailAddresses)) {
            emailAddressArray = emailAddresses.split(",");
        } else {
            emailAddressArray = null;
        }
        if (!TextUtils.isEmpty(anrs)) {
            anrArray = anrs.split(",");
        } else {
            anrArray = null;
        }
        log(" actuallyImportOneSimContact: name= " + name +
                ", phoneNumber= " + phoneNumber + ", emails= " + emailAddresses
                + ", anrs= " + anrs + ", account is " + account);
        final ArrayList<ContentProviderOperation> operationList =
                new ArrayList<ContentProviderOperation>();
        ContentProviderOperation.Builder builder =
                ContentProviderOperation.newInsert(RawContacts.CONTENT_URI);
        builder.withValue(RawContacts.AGGREGATION_MODE, RawContacts.AGGREGATION_MODE_SUSPENDED);
        if (account != null) {
            builder.withValue(RawContacts.ACCOUNT_NAME, account.name);
            builder.withValue(RawContacts.ACCOUNT_TYPE, account.type);
        }
        operationList.add(builder.build());

        builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
        builder.withValueBackReference(StructuredName.RAW_CONTACT_ID, 0);
        builder.withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE);
        builder.withValue(StructuredName.DISPLAY_NAME, name);
        operationList.add(builder.build());

        builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
        builder.withValueBackReference(Phone.RAW_CONTACT_ID, 0);
        builder.withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        builder.withValue(Phone.TYPE, Phone.TYPE_MOBILE);
        builder.withValue(Phone.NUMBER, phoneNumber);
        builder.withValue(Data.IS_PRIMARY, 1);
        operationList.add(builder.build());

        if (anrArray != null) {
            for (String anr : anrArray) {
                builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
                builder.withValueBackReference(Phone.RAW_CONTACT_ID, 0);
                builder.withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
                builder.withValue(Phone.TYPE, Phone.TYPE_HOME);
                builder.withValue(Phone.NUMBER, anr);
                operationList.add(builder.build());
            }
        }

        if (emailAddresses != null) {
            for (String emailAddress : emailAddressArray) {
                builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
                builder.withValueBackReference(Email.RAW_CONTACT_ID, 0);
                builder.withValue(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE);
                builder.withValue(Email.TYPE, Email.TYPE_MOBILE);
                builder.withValue(Email.ADDRESS, emailAddress);
                operationList.add(builder.build());
            }
        }

        try {
            resolver.applyBatch(ContactsContract.AUTHORITY, operationList);
        } catch (RemoteException e) {
            log(String.format("%s: %s", e.toString(), e.getMessage()));
        } catch (OperationApplicationException e) {
            log(String.format("%s: %s", e.toString(), e.getMessage()));
        }
    }

    /**
     * After turn on airplane mode, cancel import sim contacts operation.
     */
    private void cancelSimContactsImporting() {
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.cancel();
        }
    }
}
