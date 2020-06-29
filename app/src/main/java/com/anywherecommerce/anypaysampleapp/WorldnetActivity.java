package com.anywherecommerce.anypaysampleapp;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.anywherecommerce.android.sdk.AuthenticationListener;
import com.anywherecommerce.android.sdk.GenericEventListener;
import com.anywherecommerce.android.sdk.GenericEventListenerWithParam;
import com.anywherecommerce.android.sdk.Logger;
import com.anywherecommerce.android.sdk.MeaningfulError;
import com.anywherecommerce.android.sdk.MeaningfulErrorListener;
import com.anywherecommerce.android.sdk.MeaningfulMessage;
import com.anywherecommerce.android.sdk.RequestListener;
import com.anywherecommerce.android.sdk.SDKManager;
import com.anywherecommerce.android.sdk.Terminal;
import com.anywherecommerce.android.sdk.TerminalNotInitializedException;
import com.anywherecommerce.android.sdk.component.SignatureView;
import com.anywherecommerce.android.sdk.devices.CardReader;
import com.anywherecommerce.android.sdk.devices.CardReaderController;
import com.anywherecommerce.android.sdk.devices.MultipleBluetoothDevicesFoundListener;
import com.anywherecommerce.android.sdk.devices.bbpos.BBPOSDevice;
import com.anywherecommerce.android.sdk.devices.bbpos.BBPOSDeviceCardReaderController;
import com.anywherecommerce.android.sdk.endpoints.AnyPayTransaction;
import com.anywherecommerce.android.sdk.endpoints.prioritypayments.PriorityPaymentsEndpoint;
import com.anywherecommerce.android.sdk.endpoints.propay.PropayJsonGatewayResponse;
import com.anywherecommerce.android.sdk.endpoints.worldnet.WorldnetEndpoint;
import com.anywherecommerce.android.sdk.endpoints.worldnet.WorldnetGatewayResponse;
import com.anywherecommerce.android.sdk.models.CustomerDetails;
import com.anywherecommerce.android.sdk.models.TipLineItem;
import com.anywherecommerce.android.sdk.models.TransactionType;
import com.anywherecommerce.android.sdk.transactions.CardTransaction;
import com.anywherecommerce.android.sdk.transactions.Transaction;
import com.anywherecommerce.android.sdk.transactions.listener.CardTransactionListener;
import com.anywherecommerce.android.sdk.transactions.listener.TransactionListener;
import com.anywherecommerce.android.sdk.util.Amount;

import java.util.List;

public class WorldnetActivity extends Activity {

    protected Button btUSBConnect, btnConnectAudio, btnStartEMV, btnConnectBT, btnTerminalLogin, btnKeyedsale, btRefRefund, btUnRefRefund, btnSubmitSignature;
    protected WorldnetEndpoint endpoint;
    protected TextView txtPanel;
    protected DialogManager dialogs = new DialogManager();
    protected Transaction refTransaction;
    protected LinearLayout signatureViewLayout;
    protected SignatureView signatureView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.worldnet_activity);

        try {
            Terminal.restoreState();
            endpoint = (WorldnetEndpoint) Terminal.getInstance().getEndpoint();
        } catch (Exception ex) {
            endpoint = new WorldnetEndpoint();
            Terminal.initialize(endpoint);
        }

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
        signatureViewLayout = findViewById(R.id.signatureViewLayout);
        signatureView = findViewById(R.id.signatureView);
        btnSubmitSignature = findViewById(R.id.btnSubmitSignature);

        signatureView.setStrokeColor(Color.MAGENTA);
        signatureView.setStrokeWidth(10f);

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
//                changeLogger();

                addText("\nStarting EMV transaction");
                if (!CardReaderController.isCardReaderConnected()) {
                    addText("\r\nNo card reader connected");
                    return;
                }

                dialogs.showProgressDialog(WorldnetActivity.this, "Please Wait...");

                sendEMVTransaction();
            }
        });

        btnKeyedsale.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!TextUtils.isEmpty(endpoint.getWorldnetSecret()) && !TextUtils.isEmpty(endpoint.getWorldnetTerminalID())) {
                    addText("\r\nExecuting Keyed Sale Transaction. Please Wait...");
                    dialogs.showProgressDialog(WorldnetActivity.this, "Please Wait...");

                    sendKeyedTransaction();
                } else {
                    addText("Please validate the terminal");
                }
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
                transaction.setTotalAmount(new Amount("1"));
                transaction.setRefTransactionId(refTransaction.getExternalId());
                transaction.setTransactionType(TransactionType.REFUND);

                //t.enableLogging();
                transaction.execute(new TransactionListener() {

                    @Override
                    public void onTransactionCompleted() {
                        dialogs.hideProgressDialog();

                        if (transaction.isApproved())
                            WorldnetActivity.this.addText("----> Transaction Refunded <----");
                        else {
                            WorldnetActivity.this.addText(transaction.getResponseText());
                        }

                    }

                    @Override
                    public void onTransactionFailed(MeaningfulError reason) {
                        dialogs.hideProgressDialog();

                        WorldnetActivity.this.addText("Refund Failed");
                    }
                });
            }
        });

        btUnRefRefund.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!TextUtils.isEmpty(endpoint.getWorldnetSecret()) && !TextUtils.isEmpty(endpoint.getWorldnetTerminalID())) {

                    addText("----> Starting New Refund Transaction <----");
                    dialogs.showProgressDialog(WorldnetActivity.this, "Please Wait...");

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
                                WorldnetActivity.this.addText("----> Transaction Refunded <----");
                            else {
                                WorldnetActivity.this.addText(transaction.getResponseText());
                            }
                        }

                        @Override
                        public void onTransactionFailed(MeaningfulError meaningfulError) {
                            dialogs.hideProgressDialog();

                            WorldnetActivity.this.addText("Refund Failed");
                        }
                    });
                }
                else {
                    addText("Please validate the terminal");
                }
            }
        });

        btnSubmitSignature.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialogs.showProgressDialog(WorldnetActivity.this, "Please Wait...");
                ((CardTransaction) refTransaction).setSignature(signatureView.getSignature());

                if (refTransaction.getCustomField("completed") != null) {

                    endpoint.submitSignature(refTransaction, new RequestListener() {
                        @Override
                        public void onRequestComplete(Object o) {
                            addText("\r\n SIgnature Sent Successfully");
                        }

                        @Override
                        public void onRequestFailed(MeaningfulError meaningfulError) {
                            addText("\r\n SIgnature sent Failed");
                        }
                    });
                } else {
                    refTransaction.proceed();
                }

                addText("\r\nSending SIgnature");

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

        transaction.setOnSignatureRequiredListener(new GenericEventListener() {
            @Override
            public void onEvent() {
                addText("\r\n------>Signature Required");
                dialogs.hideProgressDialog();

                signatureViewLayout.setVisibility(View.VISIBLE);
            }
        });

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

                signatureViewLayout.setVisibility(View.GONE);
            }

            @Override
            public void onTransactionFailed(MeaningfulError reason) {
                dialogs.hideProgressDialog();
                addText("\r\n------>onTransactionFailed: " + reason.toString());

                signatureViewLayout.setVisibility(View.GONE);
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
        //t.enableLogging();
        transaction.execute(transactionListener);

    }

    private void validateConfiguration() {

        addText("Validating. Please Wait...");


        endpoint.setWorldnetSecret("WN_PROVIDED_PWD");
        endpoint.setWorldnetTerminalID("WN_PROVIDED_TID");

        endpoint.setUrl("https://testpayments.anywherecommerce.com/merchant");


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
            addText("\r\n------>onTransactionCompleted - " + refTransaction.isApproved().toString());

            if (refTransaction.getTransactionType() == TransactionType.SALE) {
                final TipLineItem tip = new TipLineItem();
                tip.amount = new Amount("23");

                refTransaction.setTip(tip);

                endpoint.submitTipAdjustment(refTransaction, tip, new RequestListener() {
                    @Override
                    public void onRequestComplete(Object o) {
                        addText("\r\n------>Tip Adjustment success");

                        CustomerDetails customerDetails = new CustomerDetails();
                        customerDetails.setEmailAddress("");
                        customerDetails.setPhoneNumber("");
                        refTransaction.setCustomerDetails(customerDetails);
                        ((AnyPayTransaction) refTransaction).update(new TransactionListener() {
                            @Override
                            public void onTransactionCompleted() {
                                addText("\r\n------>Receipt Sent - ");

                                refTransaction.addCustomField("completed", true);
                                signatureViewLayout.setVisibility(View.VISIBLE);

                            }

                            @Override
                            public void onTransactionFailed(MeaningfulError meaningfulError) {
                                addText("\r\n------>Receipt Sent Failed ");
                            }
                        });
                    }

                    @Override
                    public void onRequestFailed(MeaningfulError meaningfulError) {
                        addText("\r\n------>Tip Adjustment failed");
                    }
                });
            }
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
