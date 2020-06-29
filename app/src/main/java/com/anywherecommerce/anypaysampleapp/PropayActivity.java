package com.anywherecommerce.anypaysampleapp;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.anywherecommerce.android.sdk.AppBackgroundingManager;
import com.anywherecommerce.android.sdk.AuthenticationListener;
import com.anywherecommerce.android.sdk.GenericEventListener;
import com.anywherecommerce.android.sdk.GenericEventListenerWithParam;
import com.anywherecommerce.android.sdk.Logger;
import com.anywherecommerce.android.sdk.MeaningfulError;
import com.anywherecommerce.android.sdk.MeaningfulErrorListener;
import com.anywherecommerce.android.sdk.MeaningfulMessage;
import com.anywherecommerce.android.sdk.Terminal;
import com.anywherecommerce.android.sdk.devices.CardReader;
import com.anywherecommerce.android.sdk.devices.CardReaderController;
import com.anywherecommerce.android.sdk.devices.MultipleBluetoothDevicesFoundListener;
import com.anywherecommerce.android.sdk.devices.bbpos.BBPOSDevice;
import com.anywherecommerce.android.sdk.devices.bbpos.BBPOSDeviceCardReaderController;
import com.anywherecommerce.android.sdk.endpoints.AnyPayTransaction;
import com.anywherecommerce.android.sdk.endpoints.propay.PropayEndpoint;
import com.anywherecommerce.android.sdk.endpoints.propay.PropayJsonGatewayResponse;
import com.anywherecommerce.android.sdk.models.TransactionType;
import com.anywherecommerce.android.sdk.transactions.Transaction;
import com.anywherecommerce.android.sdk.transactions.listener.CardTransactionListener;
import com.anywherecommerce.android.sdk.transactions.listener.TransactionListener;
import com.anywherecommerce.android.sdk.util.Amount;

import java.util.List;

public class PropayActivity extends Activity {

    protected Button btUSBConnect, btnConnectAudio, btnStartEMV, btnConnectBT, btnTerminalLogin, btnKeyedsale, btRefRefund, btnCapture, btnEMVAuth;
    protected PropayEndpoint endpoint;
    protected TextView txtPanel;
    protected DialogManager dialogs = new DialogManager();
    protected Transaction refTransaction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.propay_activity);

        try {
            Terminal.restoreState();
            endpoint = (PropayEndpoint) Terminal.getInstance().getEndpoint();
        }
        catch (Exception ex) {
            endpoint = new PropayEndpoint();
            Terminal.initialize(endpoint);
        }

        AppBackgroundingManager.get().registerListener(new AppBackgroundingManager.AppBackroundedListener() {
            @Override
            public void onBecameForeground() {
                Logger.trace("Caught app in foreground.");
            }

            @Override
            public void onBecameBackground() {
                Logger.trace("Caught app in background.");
            }
        });

        btnTerminalLogin = findViewById(R.id.terminalLogin);
        btUSBConnect =  findViewById(R.id.btUSBConnect);
        btnConnectAudio =  findViewById(R.id.audioConnect);
        btnConnectBT =  findViewById(R.id.btConnect);
        btnStartEMV =  findViewById(R.id.btnStartEmvSale);
        btnKeyedsale =  findViewById(R.id.keyedsale);
        btnEMVAuth =  findViewById(R.id.emvAuth);
        btnCapture =  findViewById(R.id.capture);
        btRefRefund = findViewById(R.id.refRefund);

        txtPanel = findViewById(R.id.txtTextHarnessPanel);
        txtPanel.setMovementMethod(new ScrollingMovementMethod());


        final CardReaderController cardReaderController = CardReaderController.getControllerFor(BBPOSDevice.class);

        cardReaderController.subscribeOnCardReaderConnected(new GenericEventListenerWithParam<CardReader>() {
            @Override
            public void onEvent(CardReader deviceInfo) {

                // Set the card interface mode to enable disable INSERT, TAP, SWIPE

                /*
                EnumSet<CardInterface> enabledEntryModes = EnumSet.noneOf(CardInterface.class);
                enabledEntryModes.add(CardInterface.SWIPE);

                CardReaderController.getConnectedReader().setEnabledInterfaces(enabledEntryModes);
                */

                if (deviceInfo == null)
                    addText("\r\nUnknown device connected");
                else
                    addText("\nDevice connected " + deviceInfo.getModelDisplayName());
            }
        });

        cardReaderController.subscribeOnCardReaderDisconnected(new GenericEventListener() {
            @Override
            public void onEvent() {
                addText("\nDevice disconnected");
            }
        });

        cardReaderController.subscribeOnCardReaderConnectFailed(new MeaningfulErrorListener() {
            @Override
            public void onError(MeaningfulError error) {
                addText("\nDevice connect failed: " + error.toString());
            }
        });

        cardReaderController.subscribeOnCardReaderError(new MeaningfulErrorListener() {
            @Override
            public void onError(MeaningfulError error) {
                addText("\nDevice error: " + error.toString());
            }
        });


        btnTerminalLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                validateConfiguration();
            }
        });

        btUSBConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                addText("\nConnecting to USB Reader\r\n");
                cardReaderController.connectOther(CardReader.ConnectionMethod.USB);
            }
        });

        btnConnectAudio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                addText("\nConnecting to audio jack (with polling)\r\n");
                cardReaderController.connectAudioJack();
            }
        });

        btnConnectBT.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                addText("\nConnecting to BT\r\n");
                cardReaderController.connectBluetooth(new MultipleBluetoothDevicesFoundListener() {
                    @Override
                    public void onMultipleBluetoothDevicesFound(List<BluetoothDevice> matchingDevices) {
                        addText("Many BT devices");
                    }
                });
            }
        });

        btnStartEMV.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BBPOSDeviceCardReaderController.getController().setDebugLogEnabled(true);

                addText("\nStarting EMV transaction");
                if (!CardReaderController.isCardReaderConnected()) {
                    addText("\r\nNo card reader connected");
                    return;
                }

                dialogs.showProgressDialog(PropayActivity.this, "Please Wait...");

                sendEMVTransaction();
            }
        });

        btnKeyedsale.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                addText("\r\nExecuting Keyed Sale Transaction. Please Wait...");
                dialogs.showProgressDialog(PropayActivity.this, "Please Wait...");

                //For ProPay
                sendKeyedTransaction();
            }
        });

        btnEMVAuth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                addText("\nStarting EMV Auth transaction");
                if (!CardReaderController.isCardReaderConnected()) {
                    addText("\r\nNo card reader connected");
                    return;
                }

                dialogs.showProgressDialog(PropayActivity.this, "Please Wait...");

                sendEMVAuthTransaction();
            }
        });

        btnCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (refTransaction == null) {
                    addText("----> This captures the last Auth transaction made. Please perform a Auth transaction first. <----");
                    return;
                }

                addText("----> Performing capture on last Transaction <----");
                dialogs.showProgressDialog(PropayActivity.this, "Please Wait...");

                final AnyPayTransaction captureT = (AnyPayTransaction) refTransaction.createCapture();
                captureT.setEndpoint(refTransaction.getEndpoint());

                captureT.execute(new TransactionListener() {
                    @Override
                    public void onTransactionCompleted() {
                        dialogs.hideProgressDialog();

                        if (captureT.isApproved())
                            PropayActivity.this.addText("----> Transaction Captured <----");
                        else {
                            PropayActivity.this.addText(captureT.getGatewayResponse().getStatus());
                        }
                    }

                    @Override
                    public void onTransactionFailed(MeaningfulError reason) {
                        PropayActivity.this.addText("Capture Failed " + reason.message);
                    }
                });
            }
        });

        btRefRefund.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (refTransaction == null) {
                    addText("----> This voids the last Auth transaction made. Please perform a Auth transaction first. <----");
                    return;
                }

                addText("----> Starting Referenced Refund Transaction <----");

                final AnyPayTransaction transaction = new AnyPayTransaction();
                transaction.setEndpoint(endpoint);
                transaction.setExternalId(refTransaction.getExternalId());
                transaction.setTotalAmount(refTransaction.getTotalAmount());
                transaction.setRefTransactionId(refTransaction.getExternalId());
                transaction.setTransactionType(TransactionType.VOID);

                transaction.execute(new TransactionListener() {

                    @Override
                    public void onTransactionCompleted() {
                        dialogs.hideProgressDialog();

                        if (transaction.isApproved())
                            PropayActivity.this.addText("----> Transaction Refunded <----");
                        else {
                            PropayActivity.this.addText(transaction.getResponseText());
                        }

                    }

                    @Override
                    public void onTransactionFailed(MeaningfulError reason) {
                        dialogs.hideProgressDialog();

                        PropayActivity.this.addText("Refund Failed");
                    }
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        //Clear saved terminal using below api
//        Terminal.getInstance().clearSavedState();
    }

    private void sendKeyedTransaction() {
        final AnyPayTransaction transaction = new AnyPayTransaction();
        transaction.setEndpoint(endpoint);
        transaction.setTransactionType(TransactionType.SALE);
        transaction.setCardExpiryMonth("10");
        transaction.setCardExpiryYear("20");
        transaction.setAddress("123 Main Street");
        transaction.setPostalCode("30004");
        transaction.setCVV2("999");
        transaction.setCardholderName("Jane Doe");
        transaction.setCardNumber("4012888888881881");
        transaction.setTotalAmount(new Amount("10.47"));
        transaction.setCurrency("USD");
//        transaction.addCustomField("sessionToken", sessionToken);

        refTransaction = transaction;
        transaction.execute(transactionListener);

    }

    private void sendEMVTransaction() {
        final AnyPayTransaction transaction = new AnyPayTransaction();
        transaction.setEndpoint(endpoint);
        transaction.useCardReader(CardReaderController.getConnectedReader());
        transaction.setTransactionType(TransactionType.SALE);
        transaction.setAddress("123 Main Street");
        transaction.setPostalCode("30004");
        transaction.setTotalAmount(new Amount("12.58"));
        transaction.setCurrency("USD");
//        transaction.addCustomField("sessionToken", sessionToken);

        refTransaction = transaction;

        transaction.execute(new CardTransactionListener() {
            @Override
            public void onCardReaderEvent(MeaningfulMessage event) {
                addText("\r\n------>onCardReaderEvent: " + event.message);
            }

            @Override
            public void onTransactionCompleted() {
                dialogs.hideProgressDialog();
                addText("\r\n------>onTransactionCompleted" + transaction.isApproved().toString());
            }

            @Override
            public void onTransactionFailed(MeaningfulError reason) {
                dialogs.hideProgressDialog();
                addText("\r\n------>onTransactionFailed: " + reason.toString());
            }
        });
    }

    private void sendEMVAuthTransaction() {
        final AnyPayTransaction transaction = new AnyPayTransaction();
        transaction.setEndpoint(endpoint);
        transaction.useCardReader(CardReaderController.getConnectedReader());
        transaction.setTransactionType(TransactionType.AUTHONLY);
        transaction.setAddress("123 Main Street");
        transaction.setPostalCode("30004");
        transaction.setTotalAmount(new Amount("13.07"));
        transaction.setCurrency("USD");
//        transaction.addCustomField("sessionToken", sessionToken);

        refTransaction = transaction;

        transaction.execute(new CardTransactionListener() {
            @Override
            public void onCardReaderEvent(MeaningfulMessage event) {
                addText("\r\n------>onCardReaderEvent: " + event.message);
            }

            @Override
            public void onTransactionCompleted() {
                dialogs.hideProgressDialog();
                addText("\r\n------>onTransactionCompleted" + transaction.isApproved().toString());
            }

            @Override
            public void onTransactionFailed(MeaningfulError reason) {
                dialogs.hideProgressDialog();
                addText("\r\n------>onTransactionFailed: " + reason.toString());
            }
        });
    }

    private void validateConfiguration() {

        addText("Validating. Please Wait...");

        //For ProPay
        endpoint.setCertStr("PROPAY_PROVIDED_CERTSTR");
        endpoint.setX509Cert("PROPAY_PROVIDED_x509");
        endpoint.setXmlApiBaseUrl("https://xmltest.propay.com/api/");
        endpoint.setJsonApiBaseUrl("https://mobileapitest.propay.com/merchant.svc/json/");
        endpoint.setAccountNum("PROPAY_PROVIDED_ACNUM");
        endpoint.setTerminalId("PROPAY_PROVIDED_TID");

        endpoint.validateConfiguration(new AuthenticationListener() {
            @Override
            public void onAuthenticationComplete() {
                addText("\r\n------>Endpoint Validation Success");
            }

            @Override
            public void onAuthenticationFailed(MeaningfulError meaningfulError) {
                addText("\r\n------>Propay Validation Error: " + meaningfulError.message);
            }
        });
    }


    private TransactionListener transactionListener = new TransactionListener() {
        @Override
        public void onTransactionCompleted() {
            dialogs.hideProgressDialog();
            addText("\r\n------>onTransactionCompleted - " + ((PropayJsonGatewayResponse)refTransaction.getGatewayResponse()).getResponseJson());
        }

        @Override
        public void onTransactionFailed(MeaningfulError reason) {
            dialogs.hideProgressDialog();
            addText("\r\n------>onTransactionFailed: " + reason.toString());
        }
    };


    private void addText(String text) {
        txtPanel.append("\r\n" + text + "\r\n\n");
    }
}
