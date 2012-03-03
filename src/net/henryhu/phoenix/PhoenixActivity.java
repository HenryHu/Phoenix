package net.henryhu.phoenix;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentProviderOperation;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Data;

import android.util.Log;

class Contact {
	public String firstName, middleName, lastName;
	public String phoFirstName, phoMiddleName, phoLastName;
	long rawId;
	String displayName;
	boolean modified;
	
	public static CharSequence noPhonetic;
	
	Contact(String _firstName, String _middleName, String _lastName, 
			String _phoFirstName, String _phoMiddleName, String _phoLastName, 
			long _rawId, String _displayName) {
		firstName = _firstName;
		middleName = _middleName;
		lastName = _lastName;
		phoFirstName = _phoFirstName;
		phoMiddleName = _phoMiddleName;
		phoLastName = _phoLastName;
		rawId = _rawId;
		displayName = _displayName;
		modified = false;
	}
	
	public String toString()
	{
		StringBuilder sb = new StringBuilder();
		if (middleName != null)
		{
			sb.append(displayName);
			sb.append('(');
			sb.append(lastName);
			sb.append(" ");
			sb.append(middleName);
			sb.append(" ");
			sb.append(firstName);
			sb.append(")");
			
			sb.append(" / ");
			if (phoFirstName == null && phoLastName == null && phoMiddleName == null)
				sb.append(noPhonetic);
			else
			{
				sb.append(phoLastName);
				sb.append(" ");
				sb.append(phoMiddleName);
				sb.append(" ");
				sb.append(phoFirstName);
			}
		}
		else
		{
			sb.append(displayName);
			sb.append('(');
			sb.append(lastName);
			sb.append(" ");
			sb.append(firstName);
			sb.append(")");
			
			sb.append(" / ");
			if (phoFirstName == null && phoLastName == null)
				sb.append(noPhonetic);
			else
			{
				sb.append(phoLastName);
				sb.append(" ");
				sb.append(phoFirstName);
			}
		}
		if (modified)
			sb.append(" *");
		return sb.toString();
	}
	
	boolean isChinese() {
		return inChinese(firstName) && inChinese(middleName) && inChinese(lastName);
	}

    boolean inChinese(String str) {
    	if (str == null) return true;
    	for (int i=0; i<str.length(); i++) {
    		char ch = str.charAt(i);
    		if (ch < 256) return false;
    		if (!PhoenixActivity.transTable.containsKey(ch))
    			return false;
    	}
    	return true;
    }
}

public class PhoenixActivity extends Activity implements OnClickListener{
	Button bList, bGet, bSet, bFilter;
	ListView lContacts;
	List<Contact> contacts;
	ArrayAdapter<Contact> adpContacts;
	static HashMap<Character, List<String>> transTable;
	Activity me;
	TextView tStatus;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        me = this;
        
        bList = (Button)findViewById(R.id.bListContacts);
        bGet = (Button)findViewById(R.id.bGetPhonetics);
        bSet = (Button)findViewById(R.id.bSetPhonetics);
        bFilter = (Button)findViewById(R.id.bFilter);
        lContacts = (ListView)findViewById(R.id.lContacts);
        tStatus = (TextView)findViewById(R.id.tStatus);
               
        Contact.noPhonetic = getResources().getText(R.string.s_nopho);
        contacts = new LinkedList<Contact>();
        adpContacts = new ArrayAdapter<Contact>(this, R.layout.contact_view, R.id.tContactName, contacts); 
        lContacts.setAdapter(adpContacts);
        
        try {
			initTable();
		} catch (Exception e) {
			e.printStackTrace();
		}
        
        bList.setOnClickListener(this);
        bGet.setOnClickListener(this);
        bSet.setOnClickListener(this);
        bFilter.setOnClickListener(this);
    }
    
    void setStatus(final CharSequence status) {
    	runOnUiThread(new Runnable() {
    		@Override
    		public void run() {
    			tStatus.setText(status);
    		}
    	});
    }

    void initTable() throws Exception {
    	transTable = new HashMap<Character, List<String>>();
    	InputStream fs = getClass().getResourceAsStream("/trans_table.xml");
    	List<PhoneticPair> pairs = null;
    	try {
    		 pairs = new XmlReader().readXML(fs);
    	} catch (Exception e) {
    		Log.wtf("initTable()", e.toString());
    		throw e;
    	}
    	for (PhoneticPair pair : pairs) {
    		for (int i=0; i<pair.chars.length(); i++)
    		{
    			char ch = pair.chars.charAt(i);
    			
    			if (!transTable.containsKey(ch)) {
    				transTable.put(pair.chars.charAt(i), new ArrayList<String>());
    			}
    			String py = pair.phonetic;
        		if ((py.charAt(py.length() - 1) >= '0') && (py.charAt(py.length() - 1) <= '9'))
        			py = py.substring(0, py.length() - 1);

        		List<String> current = transTable.get(ch);
				if (!current.contains(py))
					current.add(py);
    		}
    	}
    	setStatus(getResources().getText(R.string.st_ready));
    }
    
    Object cleaner = new Object();
    boolean cleaned = false;
    void clearContacts() {
    	runOnUiThread(new Runnable() {
    		@Override
    		public void run() {
    			adpContacts.clear();
    			synchronized(cleaner) {
    				cleaned = true;
    				cleaner.notify();
    			}
    		}
    	});
    	synchronized(cleaner) {
    		if (!cleaned)
    		{
    			try {
    				cleaner.wait();
    			} catch (InterruptedException e) {
    			}
    		}
    	}
    }
    
    void listContacts() {
    	setStatus(getResources().getText(R.string.st_loading));
    	new Thread(new Runnable() {
    		@Override
    		public void run() {
    			clearContacts();
    			Uri lookupUri = Data.CONTENT_URI;
    			Cursor c = getContentResolver().query(lookupUri, new String[]{
    					StructuredName.GIVEN_NAME, StructuredName.MIDDLE_NAME, StructuredName.FAMILY_NAME, 
    					StructuredName.PHONETIC_GIVEN_NAME, StructuredName.PHONETIC_MIDDLE_NAME, StructuredName.PHONETIC_FAMILY_NAME,
    					Data.RAW_CONTACT_ID, StructuredName.DISPLAY_NAME,
    			}, Data.MIMETYPE + "=?", new String[] {StructuredName.CONTENT_ITEM_TYPE}, null);
    			try {
    				c.moveToFirst();
    				do {
    					String displayName = c.getString(7);
    					String firstName = c.getString(0);
    					String lastName = c.getString(2);
    					if (displayName != null && ! displayName.equals("") &&
    							firstName != null && lastName != null
    							)
    					{
    						Contact cc = new Contact(firstName, c.getString(1),
    								lastName, c.getString(3),
    								c.getString(4), c.getString(5), 
    								c.getLong(6), displayName);
    						if (cc.isChinese())
    							contacts.add(cc);
    					}
    				} while (c.moveToNext());
    				notifyChanged();
    			} finally {
    				c.close();
    			}
    			setStatus(getResources().getText(R.string.st_loaded));
    		}
    	}).start();
    }
    
    CharSequence phoChosen = null;
    Object waiter = new Object();
    
    public CharSequence getInput(final CharSequence title, final CharSequence[] items)
    {
    	phoChosen = null;
    	
        runOnUiThread(new Runnable() 
        {
            @Override
            public void run() 
            {
            	AlertDialog.Builder builder = 
            			new AlertDialog.Builder(me).
            			setTitle(title).setItems(items, new DialogInterface.OnClickListener() {
            			    public void onClick(DialogInterface dialog, int item) {
            			        phoChosen = items[item];
            			        synchronized(waiter) {
            			        	waiter.notify();
            			        }
            			    }
            			}).setOnCancelListener(new OnCancelListener() {

							@Override
							public void onCancel(DialogInterface arg0) {
								synchronized(waiter) {
									waiter.notify();
								}
							}
            			});

            	builder.show();   
            }
        });

        try 
        {
        	synchronized(waiter) {
        		waiter.wait();
        	}
        } 
        catch (InterruptedException e) 
        {
        }

        return phoChosen;
    }

    class CancelledException extends Exception {
		private static final long serialVersionUID = 4334778630641204956L;
    }
    
    String getPhonetic(String str, String fullName, int no, int total) throws CancelledException {
    	if (str == null) return null;
    	StringBuilder ret = new StringBuilder();
    	for (int i=0; i<str.length(); i++) {
    		List<String> pys = transTable.get(str.charAt(i));
    		String py;
    		if (pys.size() == 1)
    			py = pys.get(0);
    		else {
    			CharSequence cpy = getInput(String.format(getResources().getText(R.string.q_phosel).toString(),
    					str.charAt(i), fullName, no, total), 
    					pys.toArray(new String[]{}));
    			if (cpy == null)
    				throw new CancelledException();
    			py = cpy.toString();
    		}
    		
    		if (ret.length() == 0)
    		{
    			ret.append(py.substring(0, 1).toUpperCase());
    			ret.append(py.substring(1));
    		} else {
    			ret.append(py);
    		}
    	}
    	return ret.toString();
    }
    
    void getPhonetics() {
    	setStatus(getResources().getText(R.string.st_getpho));
    	new Thread(new Runnable() {
    		@Override
    		public void run() {
    			int cnt = 0;
    			for (Contact c : contacts) {
    				cnt++;
    				if (c.isChinese())
    				{
    					try {
    						boolean modified = false;
    						if (c.phoFirstName == null)
    						{
    							c.phoFirstName = getPhonetic(c.firstName, c.displayName, cnt, contacts.size());
    							if (c.phoFirstName != null)
    								modified = true;
    						}
    						if (c.phoMiddleName == null)
    						{
    							c.phoMiddleName = getPhonetic(c.middleName, c.displayName, cnt, contacts.size());
    							if (c.phoMiddleName != null)
    								modified = true;
    						}
    						if (c.phoLastName == null)
    						{
    							c.phoLastName = getPhonetic(c.lastName, c.displayName, cnt, contacts.size());
    							if (c.phoLastName != null)
    								modified = true;
    						}
    						if (modified)
    							c.modified = true;
    					} catch (CancelledException e) {
   							CharSequence ret = getInput(getResources().getText(R.string.st_getstop), 
   								new CharSequence[] {getResources().getText(R.string.ans_yes), 
   								getResources().getText(R.string.ans_no)});
   							if (ret == null || ret.equals(getResources().getText(R.string.ans_yes)))
   								break;
    					}
    				}
    			}

    			notifyChanged();
    			setStatus(getResources().getText(R.string.st_phogot));
    		}
    	}).start();
    }
    
    void notifyChanged() {
    	runOnUiThread(new Runnable() {
			@Override
			public void run() {
				adpContacts.notifyDataSetChanged();
			}
		});
    }
    
    void setPhonetics() {
    	setStatus(getResources().getText(R.string.st_saving));
    	new Thread(new Runnable() {
    		@Override
    		public void run() {
    			ArrayList<ContentProviderOperation> ops =
    					new ArrayList<ContentProviderOperation>();

    			for (Contact c : contacts) {
    				if (c.modified) {
    					ops.add(ContentProviderOperation.newUpdate(Data.CONTENT_URI)
    							.withSelection(StructuredName.RAW_CONTACT_ID + "=? AND "
    									+ Data.MIMETYPE + "=?",
    									new String[] { String.valueOf(c.rawId),
    									StructuredName.CONTENT_ITEM_TYPE})
    									.withValue(StructuredName.PHONETIC_GIVEN_NAME, c.phoFirstName)
    									.withValue(StructuredName.PHONETIC_MIDDLE_NAME, c.phoMiddleName)
    									.withValue(StructuredName.PHONETIC_FAMILY_NAME, c.phoLastName)
    									.build());
    				}
    			}

    			try {
    				getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
    			} catch (RemoteException e) {
    				e.printStackTrace();
    				Log.e("setPhonetics()", "apply fail! " + e.getMessage());
    			} catch (OperationApplicationException e) {
    				e.printStackTrace();
    				Log.e("setPhonetics()", "apply fail! " + e.getMessage());
    			}
    			setStatus(getResources().getText(R.string.st_saved));
    		}
    		
    	}).start();
    }
    
    void filterResults() {
    	setStatus(getResources().getText(R.string.st_filter));
    	for (int i=0; i<contacts.size(); i++) {
    		Contact c = contacts.get(i);
    		if (!c.modified) {
    			contacts.remove(i);
    			i--;
    		}
    	}
    	setStatus(getResources().getText(R.string.st_filtered));
    	notifyChanged();
    }

	@Override
	public void onClick(View arg0) {
		if (arg0 == bList) {
			listContacts();
		} else if (arg0 == bGet) {
			getPhonetics();
		} else if (arg0 == bSet) {
			setPhonetics();
		} else if (arg0 == bFilter) {
			filterResults();
		}
		
	}
}