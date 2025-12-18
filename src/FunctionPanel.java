import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Panel bên phải - Hiển thị chức năng theo role (Employee / Admin)
 */
public class FunctionPanel extends JPanel {
    private final JTextArea logArea;
    private final JPanel controlPanel;
    private final JLabel roleLabel;
    private String currentRole;
    private final PcscClient pcsc;
    private CardData currentCard;

    public FunctionPanel(PcscClient pcsc) {
        this.pcsc = pcsc;
        this.currentRole = "EMPLOYEE"; // Mặc định

        setLayout(new BorderLayout());
        setBackground(new Color(248, 250, 252));

        // Register as card event listener
        CardEventBroadcaster.getInstance().addCardListener(card -> {
            SwingUtilities.invokeLater(() -> displayCardInfo(card));
        });
        
        // Register as purchase approval listener
        CardEventBroadcaster.getInstance().addPurchaseListener((items, totalPrice) -> {
            return showPurchaseApprovalDialog(items, totalPrice);
        });
        
        // Register as topup approval listener
        CardEventBroadcaster.getInstance().addTopupListener((amount, paymentMethod) -> {
            return showTopupApprovalDialog(amount, paymentMethod);
        });

        // Top: Tiêu đề vai trò - Modern gradient
        JPanel topPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gradient = new GradientPaint(0, 0, new Color(103, 58, 183), 
                                                           getWidth(), 0, new Color(156, 39, 176));
                g2d.setPaint(gradient);
                g2d.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        topPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 12));
        roleLabel = new JLabel("👤 NHÂN VIÊN");
        roleLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        roleLabel.setForeground(Color.WHITE);
        topPanel.add(roleLabel);
        topPanel.setPreferredSize(new Dimension(800, 55));
        add(topPanel, BorderLayout.NORTH);

        // Center: Log area - Modern styling
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setBackground(new Color(245, 245, 250));
        logArea.setForeground(new Color(30, 40, 50));
        logArea.setMargin(new Insets(10, 10, 10, 10));
        logArea.setText("Sẵn sàng\n");
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 210), 1));
        add(scrollPane, BorderLayout.CENTER);

        // Bottom: Control buttons - Modern layout
        controlPanel = new JPanel();
        controlPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 12, 12));
        controlPanel.setBackground(new Color(248, 250, 252));
        controlPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(200, 200, 210)));

        // Nút cho Employee (mặc định)
        addEmployeeButtons();

        add(controlPanel, BorderLayout.SOUTH);
    }

    private void addEmployeeButtons() {
        controlPanel.removeAll();
        logArea.setText("👤 Nhân Viên: Quẹt thẻ khách để xem thông tin\n");

        JButton swipeBtn = createModernButton("🔷 Quẹt Thẻ Khách", new Color(33, 150, 243));
        swipeBtn.addActionListener(e -> readCustomerCard());
        controlPanel.add(swipeBtn);

        controlPanel.revalidate();
        controlPanel.repaint();
    }

    private void addAdminButtons() {
        controlPanel.removeAll();
        logArea.setText("🔐 Admin: Tạo hoặc quẹt thẻ\n");

        JButton createBtn = createModernButton("➕ Tạo Thẻ Mới", new Color(76, 175, 80));
        createBtn.addActionListener(e -> createNewCard());
        controlPanel.add(createBtn);

        JButton swipeBtn = createModernButton("🔷 Quẹt Thẻ", new Color(255, 152, 0));
        swipeBtn.addActionListener(e -> readCustomerCard());
        controlPanel.add(swipeBtn);
        
        JButton deleteBtn = createModernButton("🗑️ Xóa Thẻ", new Color(244, 67, 54));
        deleteBtn.addActionListener(e -> deleteCard());
        controlPanel.add(deleteBtn);

        controlPanel.revalidate();
        controlPanel.repaint();
    }

    public void switchRole(String role) {
        this.currentRole = role;

        if (role.equals("ADMIN")) {
            roleLabel.setText("🔐 ADMIN");
            addAdminButtons();
        } else {
            roleLabel.setText("👤 NHÂN VIÊN");
            addEmployeeButtons();
        }
    }

    private void readCustomerCard() {
        logArea.setText("");
        logArea.append("[TIẾN HÀNH] Đặt thẻ vào đúng vị trí...\n");

        new Thread(() -> {
            try {
                Thread.sleep(500);
                pcsc.connectFirstPresentOrFirst();
                logArea.append("[OK] Kết nối thẻ thành công!\n\n");

                // Select applet
                javax.smartcardio.CommandAPDU selectCmd = new javax.smartcardio.CommandAPDU(0x00, 0xA4, 0x04, 0x00,
                        new byte[]{(byte)0x26,(byte)0x12,(byte)0x20,(byte)0x03,(byte)0x20,(byte)0x03,(byte)0x00});
                javax.smartcardio.ResponseAPDU selectResp = pcsc.transmit(selectCmd);

                if ((selectResp.getSW() & 0xFF00) != 0x9000) {
                    logArea.append("[LỖI] Không kết nối được applet\n");
                    return;
                }

                // Read
                javax.smartcardio.CommandAPDU readCmd = CardHelper.buildReadCommand();
                javax.smartcardio.ResponseAPDU readResp = pcsc.transmit(readCmd);

                if ((readResp.getSW() & 0xFF00) != 0x9000) {
                    logArea.append("[LỖI] Không đọc được dữ liệu\n");
                    return;
                }

                currentCard = CardHelper.parseReadResponse(readResp.getData());
                logArea.append("==== THÔNG TIN KHÁCH HÀNG ====\n");
                logArea.append("ID: " + currentCard.userId + "\n");
                logArea.append("Số Dư: " + String.format("%,d VND", currentCard.balance) + "\n");
                logArea.append("Hạn Tập: " + currentCard.expiryDays + " ngày\n");
                logArea.append("Gói: " + currentCard.getPackageName() + "\n");

            } catch (Exception ex) {
                logArea.append("[LỖI] " + ex.getMessage() + "\n");
            }
        }).start();
    }

    private void createNewCard() {
        JPanel panel = new JPanel(new GridLayout(5, 2, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JTextField userIdField = new JTextField("1");
        JTextField balanceField = new JTextField("500000");
        JTextField expiryField = new JTextField("365");
        JTextField pinField = new JTextField("0");
        String[] packages = {"Basic", "Silver", "Gold", "Platinum"};
        JComboBox<String> packageCombo = new JComboBox<>(packages);

        panel.add(new JLabel("ID Thẻ:"));
        panel.add(userIdField);
        panel.add(new JLabel("Số Dư (VND):"));
        panel.add(balanceField);
        panel.add(new JLabel("Hạn Tập (ngày):"));
        panel.add(expiryField);
        panel.add(new JLabel("PIN (0-255):"));
        panel.add(pinField);
        panel.add(new JLabel("Gói Tập:"));
        panel.add(packageCombo);

        int option = JOptionPane.showConfirmDialog(this, panel, "Tạo Thẻ Mới", JOptionPane.OK_CANCEL_OPTION);
        if (option != JOptionPane.OK_OPTION) return;

        try {
            CardData newCard = new CardData();
            newCard.userId = (short) Integer.parseInt(userIdField.getText());
            newCard.balance = Integer.parseInt(balanceField.getText());
            newCard.expiryDays = (short) Integer.parseInt(expiryField.getText());
            newCard.pin = (byte) Integer.parseInt(pinField.getText());
            newCard.pinRetry = 5; // Default 5 attempts
            newCard.packageType = (byte) packageCombo.getSelectedIndex(); // 0=Basic, 1=Silver, 2=Gold, 3=Platinum

            logArea.append("\n[TIẾN HÀNH] Ghi thẻ...\n");
            
            javax.smartcardio.CommandAPDU writeCmd = CardHelper.buildWriteCommand(newCard);
            javax.smartcardio.ResponseAPDU writeResp = pcsc.transmit(writeCmd);

            if ((writeResp.getSW() & 0xFF00) == 0x9000) {
                logArea.append("[OK] Tạo thẻ thành công!\n");
                logArea.append("ID: " + newCard.userId + "\n");
                logArea.append("Số Dư: " + newCard.balance + " VND\n");
                logArea.append("Hạn Tập: " + newCard.expiryDays + " ngày\n");
            } else {
                logArea.append("[LỖI] Tạo thẻ thất bại\n");
            }
        } catch (Exception ex) {
            logArea.append("[LỖI] " + ex.getMessage() + "\n");
        }
    }

    /**
     * Helper method to create modern styled buttons for admin panel
     */
    private JButton createModernButton(String text, Color bgColor) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btn.setBackground(bgColor);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createRaisedBevelBorder());
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                btn.setBackground(brighten(bgColor, 20));
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                btn.setBackground(bgColor);
            }
        });
        return btn;
    }

    /**
     * Helper to brighten color on hover
     */
    private Color brighten(Color c, int amount) {
        int r = Math.min(255, c.getRed() + amount);
        int g = Math.min(255, c.getGreen() + amount);
        int b = Math.min(255, c.getBlue() + amount);
        return new Color(r, g, b);
    }
    
    /**
     * Display card info when customer swipes (real-time sync)
     */
    private void displayCardInfo(CardData card) {
        logArea.setText("");
        logArea.append("═══ KHÁCH HÀNG QUẸT THẺ ═══\n\n");
        logArea.append("ID Thẻ: " + card.userId + "\n");
        logArea.append("Số Dư: " + String.format("%,d VND", card.balance) + "\n");
        logArea.append("Hạn Tập: " + card.expiryDays + " ngày\n");
        logArea.append("Gói: " + card.getPackageName() + "\n");
        
        if (currentRole.equals("ADMIN")) {
            logArea.append("\n⚠️ ADMIN INFO:\n");
            logArea.append("Retry Counter: " + card.pinRetry + "/5\n");
            logArea.append("Status: " + (card.isLocked() ? "🔒 LOCKED" : "✓ Active") + "\n");
        }
        
        if (card.expiryDays <= 0) {
            logArea.append("\n❌ THẺ HẾT HẠN!\n");
        } else if (card.expiryDays <= 7) {
            logArea.append("\n⚠️ THẺ SẮP HẾT HẠN!\n");
        }
    }
    
    /**
     * Show purchase approval dialog (called from customer window)
     */
    private boolean showPurchaseApprovalDialog(List<CardEventBroadcaster.CartItem> items, int totalPrice) {
        StringBuilder message = new StringBuilder();
        message.append("YÊU CẦU MUA HÀNG TỪ KHÁCH:\n\n");
        for (CardEventBroadcaster.CartItem item : items) {
            message.append("• ").append(item.item.name)
                   .append(" x").append(item.quantity)
                   .append(" = ").append(item.item.price * item.quantity).append(" VND\n");
        }
        message.append("\nTổng cộng: ").append(totalPrice).append(" VND\n\n");
        message.append("Kiểm tra kho và xác nhận?");
        
        int result = JOptionPane.showConfirmDialog(
            this,
            message.toString(),
            "🛒 Xác Nhận Mua Hàng",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );
        
        boolean approved = (result == JOptionPane.YES_OPTION);
        
        logArea.append("\n" + (approved ? "[✓ CHẤP NHẬN]" : "[✗ TỪ CHỐI]") + " Đơn hàng " + totalPrice + " VND\n");
        
        return approved;
    }
    
    private boolean showTopupApprovalDialog(int amount, String paymentMethod) {
        String icon = paymentMethod.contains("QR") ? "📱" : "💵";
        String message = "YÊU CẦU NẠP TIỀN TỪ KHÁCH:\n\n" +
                        icon + " Phương thức: " + paymentMethod + "\n" +
                        "💰 Số tiền: " + String.format("%,d VND", amount) + "\n\n" +
                        "Xác nhận đã nhận tiền?";
        
        int result = JOptionPane.showConfirmDialog(
            this,
            message,
            "💳 Xác Nhận Nạp Tiền",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );
        
        boolean approved = (result == JOptionPane.YES_OPTION);
        
        logArea.append("\n" + (approved ? "[✓ CHẤP NHẬN]" : "[✗ TỪ CHỐI]") + 
                      " Nạp " + String.format("%,d VND", amount) + " (" + paymentMethod + ")\n");
        
        return approved;
    }
    
    /**
     * Delete card - Admin only
     */
    private void deleteCard() {
        logArea.setText("");
        logArea.append("[ADMIN] Xóa thẻ người dùng\n\n");
        
        try {
            pcsc.connectFirstPresentOrFirst();
            logArea.append("[OK] Kết nối thẻ thành công!\n\n");
            
            // Select applet
            javax.smartcardio.CommandAPDU selectCmd = new javax.smartcardio.CommandAPDU(0x00, 0xA4, 0x04, 0x00,
                    new byte[]{0x01, 0x02, 0x03, 0x04, 0x05, 0x06});
            javax.smartcardio.ResponseAPDU selectResp = pcsc.transmit(selectCmd);
            if ((selectResp.getSW() & 0xFF00) != 0x9000) {
                logArea.append("[LỖI] Không thể select applet\n");
                return;
            }
            
            // Read current data first
            javax.smartcardio.CommandAPDU readCmd = CardHelper.buildReadCommand();
            javax.smartcardio.ResponseAPDU readResp = pcsc.transmit(readCmd);
            
            if ((readResp.getSW() & 0xFF00) == 0x6C00) {
                int correctLE = readResp.getSW2();
                readCmd = new javax.smartcardio.CommandAPDU(0x00, 0x11, 0x00, 0x00, correctLE);
                readResp = pcsc.transmit(readCmd);
            }
            
            if ((readResp.getSW() & 0xFF00) != 0x9000) {
                logArea.append("[LỖI] Đọc thẻ thất bại\n");
                return;
            }
            
            CardData card = CardHelper.parseReadResponse(readResp.getData());
            
            // Confirm deletion
            String confirmMsg = "XÓA THẺ NGƯỜI DÙNG?\n\n" +
                              "ID: " + card.userId + "\n" +
                              "Số Dư: " + card.balance + " VND\n" +
                              "Hạn Tập: " + card.expiryDays + " ngày\n\n" +
                              "Hành động này KHÔNG THỂ HOÀN TÁC!";
            
            int confirm = JOptionPane.showConfirmDialog(
                this,
                confirmMsg,
                "⚠️ Xác Nhận Xóa",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            );
            
            if (confirm != JOptionPane.YES_OPTION) {
                logArea.append("[HỦY] Không xóa thẻ\n");
                return;
            }
            
            // Reset card to zero values
            CardData emptyCard = new CardData();
            emptyCard.userId = 0;
            emptyCard.balance = 0;
            emptyCard.expiryDays = 0;
            emptyCard.pin = 0;
            emptyCard.pinRetry = 5;
            emptyCard.packageType = 0; // Basic
            
            javax.smartcardio.CommandAPDU writeCmd = CardHelper.buildWriteCommand(emptyCard);
            javax.smartcardio.ResponseAPDU writeResp = pcsc.transmit(writeCmd);
            
            if ((writeResp.getSW() & 0xFF00) == 0x9000) {
                logArea.append("[THÀNH CÔNG] Đã xóa thẻ ID: " + card.userId + "\n");
                logArea.append("Thẻ đã được reset về mặc định\n");
                JOptionPane.showMessageDialog(this, 
                    "Xóa thẻ thành công!\nThẻ đã được reset.",
                    "Thành Công",
                    JOptionPane.INFORMATION_MESSAGE);
            } else {
                logArea.append("[LỖI] Xóa thẻ thất bại (SW: " + 
                             Integer.toHexString(writeResp.getSW()).toUpperCase() + ")\n");
            }
            
        } catch (Exception ex) {
            logArea.append("[LỖI] " + ex.getMessage() + "\n");
        }
    }
}


