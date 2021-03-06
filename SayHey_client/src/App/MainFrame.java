package App;

import javax.swing.*;
import javax.sound.sampled.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
/**
 * Created by HP on 28.04.2017.
 */
public class MainFrame extends JFrame {
    private JButton talkButton;
    private JPanel root;
    private JButton onOffButton;
    private JButton changeCanalButton;
    private JTextField myIPField;
    private JTextField serverIPField;
    private JTextPane myIPTextPane;
    private JTextPane serverIPTextPane;
    private JComboBox canalBox;
    private JRadioButton radioButton1;

    boolean stopCapture = false;
    boolean stopPlayback = true;
    volatile AudioFormat audioFormat;
    volatile TargetDataLine targetDataLine;
    volatile SourceDataLine sourceDataLine;
    AudioInputStream audioInputStream;

    int chunkSize = 2000;

    volatile byte tempBuffer[] = new byte[chunkSize];
    volatile byte tempBuffer2[] = new byte[chunkSize];
    volatile boolean tempBufferFlag = true;
    volatile boolean tempBufferFlag2 = true;
    volatile DatagramSocket s;
    volatile  InetAddress ipAdress;
    InetAddress myIpAdress;
    String ip = "192.168.43.2";
    volatile int blinkTimeout = 500;
    volatile int timeout = 1;

    public MainFrame(){
        super("SayHey client");
        setPreferredSize(new Dimension(480, 320));
        setMinimumSize(new Dimension(480, 320));
        pack();
        setLocationRelativeTo(null);
        setContentPane(root);
        setVisible(true);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);



        WindowListener exitListener = new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                int confirm = JOptionPane.showOptionDialog(
                        null, "Are You Sure to Close Application?", "Exit Confirmation", JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE, null, null, null);
                if (confirm == 0) {
                    System.exit(0);
                }
            }
        };
        addWindowListener(exitListener);

        talkButton.addMouseListener(new MouseListener() {
            public void mouseClicked(MouseEvent e) {}
            public void mouseEntered(MouseEvent e) {}
            public void mouseExited(MouseEvent e) {}
            public void mousePressed(MouseEvent e) {
                doOnPressedAction();
            }
            public void mouseReleased(MouseEvent e) {
                doOnReleasedAction();
            }
        });
        talkButton.addKeyListener(new KeyListener(){
            public void keyTyped(KeyEvent e) {}

            public void keyPressed(KeyEvent e) {
                if (talkButton.getModel().isPressed()) {
                    doOnPressedAction();
                } else {
                    // just in case it can happen that the button is released on
                    // a key press action (maybe another controls key listener...)
                    doOnReleasedAction();
                }
            }

            public void keyReleased(KeyEvent e) {
                doOnReleasedAction();
            }
        });

        try{
            myIpAdress = InetAddress.getLocalHost();
            myIPField.setText(myIpAdress.getHostAddress());

            ipAdress = InetAddress.getByName(ip);
            s = new DatagramSocket(8033);

            audioFormat = getAudioFormat();

            Mixer.Info[] mixerInfo = AudioSystem.getMixerInfo();
            System.out.println("Available mixers:");
            for(int cnt = 0; cnt < mixerInfo.length; cnt++){
                System.out.println(mixerInfo[cnt].getName());
            }

            Mixer mixer = AudioSystem.getMixer(mixerInfo[3]);

            DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, audioFormat);
            targetDataLine = (TargetDataLine)mixer.getLine(dataLineInfo);
            DataLine.Info sourceDataLineInfo = new DataLine.Info(SourceDataLine.class, audioFormat);
            sourceDataLine = (SourceDataLine)AudioSystem.getLine(sourceDataLineInfo);

            talkButton.addActionListener(new ActionListener(){
                                             public void actionPerformed(
                                                     ActionEvent e){
                                                 talkButton.setEnabled(true);

                                                 ip = serverIPField.getText();
                                                 captureAudio();
                                             }
                                         }
            );
        }catch(Exception e){
            System.out.println(e);
            System.exit(1);
        }
        onOffButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if(stopPlayback){
                    stopPlayback = false;
                    radioButton1.setSelected(true);
                    talkButton.setEnabled(true);
                    Thread playAudio = new PlayAudio();
                    playAudio.start();
                    Thread offTalkButton = new switchTalkButton();
                    offTalkButton.start();
                }else{
                    stopPlayback = true;
                    radioButton1.setSelected(false);
                    talkButton.setEnabled(false);
                }
            }
        });
    }

    private AudioFormat getAudioFormat(){
        float sampleRate = 8000.0F;
        int sampleSizeInBits = 8;
        int channels = 1;
        boolean signed = true;
        boolean bigEndian = false;
        return new AudioFormat(
                sampleRate,
                sampleSizeInBits,
                channels,
                signed,
                bigEndian);
    }

    private void captureAudio(){
        try{
            ipAdress = InetAddress.getByName(ip);
            stopCapture = false;
            targetDataLine.open(audioFormat);
            targetDataLine.start();
            Thread captureThread = new CaptureThread();
            captureThread.start();
            Thread sendThread = new sendThread();
            sendThread.start();
        } catch (Exception e) {
            System.out.println(e);
            System.exit(0);
        }
    }
    public void doOnPressedAction() {
        if(talkButton.isEnabled()){
            ip = serverIPField.getText();
            captureAudio();
        }
    }
    public void doOnReleasedAction() {
        stopCapture = true;
        targetDataLine.close();
    }
    class PlayAudio extends Thread {
        public void run(){
            try{

                byte audioData[] = new byte[chunkSize];

                sourceDataLine.open(audioFormat);
                sourceDataLine.start();

                DatagramPacket pac = new DatagramPacket(audioData, audioData.length);

                while(!stopPlayback) {
                    s.receive(pac);
                    timeout = 0;
                    talkButton.setEnabled(false);
                    InputStream byteArrayInputStream = new ByteArrayInputStream(audioData);
                    audioInputStream = new AudioInputStream(byteArrayInputStream,audioFormat,audioData.length / audioFormat.getFrameSize());
                    Thread playThread = new PlayThread();
                    playThread.start();
                }

                sourceDataLine.drain();
                sourceDataLine.close();
            } catch (Exception e) {
                System.out.println(e);
                System.exit(0);
            }
        }

    }

    class switchTalkButton extends Thread{
        public void run(){
            try{
                while(!stopPlayback){
                    if(timeout == 0){
                        timeout = 1;
                        sleep(blinkTimeout);
                    }else{
                        talkButton.setEnabled(true);
                    }
                }
            }catch (Exception e) {
                System.out.println(e);
                System.exit(0);
            }
        }
    }
    class sendThread extends Thread{
        public void run(){
            try{
                DatagramPacket pac;
                while(!stopCapture) {
                    if (!tempBufferFlag) {
                        pac = new DatagramPacket(tempBuffer, tempBuffer.length, ipAdress, 8033);
                        s.send(pac);//отправление пакета
                        tempBufferFlag = true;
                        System.out.println("Файл1 отправлен");
                    }
                    if (!tempBufferFlag2) {
                        pac = new DatagramPacket(tempBuffer2, tempBuffer2.length, ipAdress, 8033);
                        s.send(pac);//отправление пакета
                        tempBufferFlag2 = true;
                        System.out.println("Файл2 отправлен");
                    }
                }
            }catch (Exception e) {
                System.out.println(e);
                System.exit(0);
            }//end catch
        }
    }

    class CaptureThread extends Thread{
        int cnt;
        public void run(){
            try{
                while(!stopCapture){
                    if(tempBufferFlag){
                        cnt = targetDataLine.read(tempBuffer,0, tempBuffer.length);
                        if(cnt > 0){
                            tempBufferFlag = false;
                        }
                    }else {
                        if (tempBufferFlag2) {
                            cnt = targetDataLine.read(tempBuffer2, 0, tempBuffer.length);
                            if (cnt > 0) {
                                tempBufferFlag2 = false;
                            }
                        }
                    }
                }
            }catch (Exception e) {
                System.out.println(e);
                System.exit(0);
            }
        }
    }

    class PlayThread extends Thread{
        byte tempBuffer[] = new byte[chunkSize];

        public void run(){
            try{

                int cnt;
                while((cnt = audioInputStream.read(tempBuffer, 0, tempBuffer.length)) != -1){
                    if(cnt > 0){
                        sourceDataLine.write(tempBuffer,0,cnt);
                    }
                }
            }catch (Exception e) {
                System.out.println(e);
                System.exit(0);
            }
        }
    }


}
