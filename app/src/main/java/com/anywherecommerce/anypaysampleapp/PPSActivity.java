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
import com.anywherecommerce.android.sdk.SDKManager;
import com.anywherecommerce.android.sdk.Terminal;
import com.anywherecommerce.android.sdk.devices.CardReader;
import com.anywherecommerce.android.sdk.devices.CardReaderController;
import com.anywherecommerce.android.sdk.devices.MultipleBluetoothDevicesFoundListener;
import com.anywherecommerce.android.sdk.devices.bbpos.BBPOSDevice;
import com.anywherecommerce.android.sdk.devices.bbpos.BBPOSDeviceCardReaderController;
import com.anywherecommerce.android.sdk.endpoints.AnyPayTransaction;
import com.anywherecommerce.android.sdk.endpoints.prioritypayments.PriorityPaymentsEndpoint;
import com.anywherecommerce.android.sdk.endpoints.prioritypayments.PriorityPaymentsGatewayResponse;
import com.anywherecommerce.android.sdk.endpoints.propay.PropayJsonGatewayResponse;
import com.anywherecommerce.android.sdk.models.TransactionType;
import com.anywherecommerce.android.sdk.transactions.Transaction;
import com.anywherecommerce.android.sdk.transactions.listener.CardTransactionListener;
import com.anywherecommerce.android.sdk.transactions.listener.TransactionListener;
import com.anywherecommerce.android.sdk.util.Amount;

import java.util.List;

public class PPSActivity extends Activity {

    protected Button btUSBConnect, btnConnectAudio, btnStartEMV, btnConnectBT, btnTerminalLogin, btnKeyedsale, btRefRefund, btUnRefRefund;
    protected PriorityPaymentsEndpoint endpoint;
    protected TextView txtPanel;
    protected DialogManager dialogs = new DialogManager();
    protected Transaction refTransaction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pps_activity);

        try {
            Terminal.restoreState();
            endpoint = (PriorityPaymentsEndpoint) Terminal.getInstance().getEndpoint();
        }
        catch (Exception ex) {
            endpoint = new PriorityPaymentsEndpoint();
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

        txtPanel = findViewById(R.id.txtTextHarnessPanel);
        txtPanel.setMovementMethod(new ScrollingMovementMethod());

        btnTerminalLogin = findViewById(R.id.terminalLogin);
        btUSBConnect = findViewById(R.id.btUSBConnect);
        btnConnectAudio = findViewById(R.id.audioConnect);
        btnConnectBT = findViewById(R.id.btConnect);
        btnStartEMV = findViewById(R.id.btnStartEmvSale);
        btnKeyedsale = findViewById(R.id.keyedsale);
        btRefRefund = findViewById(R.id.refRefund);
        btUnRefRefund = findViewById(R.id.unrefRefund);


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

                dialogs.showProgressDialog(PPSActivity.this, "Please Wait...");

                sendEMVTransaction();
            }
        });

        btnKeyedsale.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                addText("\r\nExecuting Keyed Sale Transaction. Please Wait...");
                dialogs.showProgressDialog(PPSActivity.this, "Please Wait...");

                sendKeyedTransaction();
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

                //t.enableLogging();
                transaction.execute(new TransactionListener() {

                    @Override
                    public void onTransactionCompleted() {
                        dialogs.hideProgressDialog();

                        if (transaction.isApproved())
                            PPSActivity.this.addText("----> Transaction Refunded <----");
                        else {
                            PPSActivity.this.addText(transaction.getResponseText());
                        }

                    }

                    @Override
                    public void onTransactionFailed(MeaningfulError reason) {
                        dialogs.hideProgressDialog();

                        PPSActivity.this.addText("Refund Failed");
                    }
                });
            }
        });

        btUnRefRefund.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                addText("----> Starting New Refund Transaction <----");
                dialogs.showProgressDialog(PPSActivity.this, "Please Wait...");

                final AnyPayTransaction transaction = new AnyPayTransaction();
                transaction.setEndpoint(endpoint);
                transaction.setTransactionType(TransactionType.REFUND);
                transaction.setTotalAmount(new Amount("20.25"));
                transaction.setCardExpiryMonth("10");
                transaction.setCardExpiryYear("20");
                transaction.setAddress("123 Main Street");
                transaction.setPostalCode("30004");
                transaction.setCVV2("999");
                transaction.setCardholderName("Jane Doe");
                transaction.setCardNumber("4012888888881881");
                transaction.execute(new TransactionListener() {
                    @Override
                    public void onTransactionCompleted() {
                        dialogs.hideProgressDialog();

                        if (transaction.isApproved())
                            PPSActivity.this.addText("----> Transaction Refunded <----");
                        else {
                            PPSActivity.this.addText(transaction.getResponseText());
                        }
                    }

                    @Override
                    public void onTransactionFailed(MeaningfulError meaningfulError) {
                        dialogs.hideProgressDialog();

                        PPSActivity.this.addText("Refund Failed");
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

    private void sendEMVTransaction() {
        final AnyPayTransaction transaction = new AnyPayTransaction();
        transaction.setEndpoint(endpoint);
        transaction.useCardReader(CardReaderController.getConnectedReader());
        transaction.setTransactionType(TransactionType.SALE);
        transaction.setAddress("123 Main Street");
        transaction.setPostalCode("30004");
        transaction.setTotalAmount(new Amount("10.47"));
        transaction.setCurrency("USD");

        refTransaction = transaction;

        //t.enableLogging();
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

        refTransaction = transaction;
        transaction.execute(transactionListener);

    }

    private void validateConfiguration() {

        addText("Validating. Please Wait...");

        endpoint.setConsumerKey("PPS_PROVIDED_KEY");
        endpoint.setSecret("PPS_PROVIDED_SECRET");
        endpoint.setMerchantId("PPS_PROVIDED_MID");

        endpoint.setUrl("https://sandbox.api.mxmerchant.com/checkout/v3/");

        endpoint.validateConfiguration(new AuthenticationListener() {
            @Override
            public void onAuthenticationComplete() {
                addText("\r\n------> Terminal Validation Success");
            }

            @Override
            public void onAuthenticationFailed(MeaningfulError meaningfulError) {
                addText("\r\n------> Terminal Validation Failed");
            }
        });

    }


    private TransactionListener transactionListener = new TransactionListener() {
        @Override
        public void onTransactionCompleted() {
            dialogs.hideProgressDialog();
            addText("\r\n------>onTransactionCompleted - " + ((PriorityPaymentsGatewayResponse)refTransaction.getGatewayResponse()).getResponseJson());
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
