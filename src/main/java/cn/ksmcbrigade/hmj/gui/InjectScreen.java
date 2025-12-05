package cn.ksmcbrigade.hmj.gui;

import cn.ksmcbrigade.hmj.HotModInjector;
import cn.ksmcbrigade.hmj.utils.ModInjector;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import java.awt.*;
import java.io.File;
import java.util.Objects;

public class InjectScreen extends Screen {
    private static final Text ENTER_IP_TEXT = Text.literal("File path");
    private ButtonWidget selectServerButton;
    private TextFieldWidget addressField;
    private final Screen parent;

    private Text installActive = Text.of("");

    public InjectScreen(Screen parent) {
        super(Text.of("Install Mod"));
        this.parent = parent;
        this.client = MinecraftClient.getInstance();
    }

    public void tick() {
        this.addressField.tick();
    }

    public void init() {
        this.addressField = new TextFieldWidget(this.textRenderer, this.width / 2 - 100, 116, 200, 20, Text.literal("File path"));
        this.addressField.setMaxLength(320);
        this.addressField.setChangedListener((context)-> this.selectServerButton.active = !context.isEmpty() && new File(context).exists() && (addressField.getText().endsWith(".jar") || addressField.getText().endsWith(".zip")));
        this.addressField.setFocusUnlocked(true);
        this.addSelectableChild(this.addressField);
        this.selectServerButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Install"), (button) -> {
            try {
                installActive = Text.literal("Installing...");
                ModInjector.install(new File(addressField.getText()),false,false,true);
                installActive = Text.literal("Installation success!");
            } catch (Exception e) {
                e.printStackTrace();
                installActive = Text.literal("Error in install the mod: "+e.getMessage());
            }
        }).dimensions(this.width / 2 - 100, this.height / 4 + 96 + 12, 200, 20).build());
        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, (button) -> {
            this.client.setScreen(this.parent);
        }).dimensions(this.width / 2 - 100, this.height / 4 + 120 + 12, 200, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.of("..."),(button)->{
            try {
                this.addressField.setText(Objects.requireNonNull(HotModInjector.selectMod()).getPath());
            } catch (Exception e) {
                //nothing
            }
        }).dimensions(this.width / 2 + 102,116,25,20).build());
        this.setInitialFocus(this.addressField);
        this.selectServerButton.active = this.addressField.active = !addressField.getText().isEmpty() && new File(addressField.getText()).exists() && (addressField.getText().endsWith(".jar") || addressField.getText().endsWith(".zip"));
    }

    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 16777215);
        context.drawTextWithShadow(this.textRenderer, ENTER_IP_TEXT, this.width / 2 - 100, 100, 10526880);
        context.drawTextWithShadow(this.textRenderer,this.installActive,2,this.height-10,this.installActive.getString().contains("success")? Color.YELLOW.getRGB() : (this.installActive.getString().contains("Error in install the mod")?Color.RED.getRGB():Color.WHITE.getRGB()));
        this.addressField.render(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
    }
}
