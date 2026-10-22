/*
 * EMCL (Eternal Minecraft Launcher)
 * Copyright (C) 2026  ETC Team <2416444244@qq.com>
 *
 * Forked from Hello Minecraft! Launcher (HMCL)
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.ui.main;

import com.jfoenix.controls.JFXButton;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.DialogUtils;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.WeakListenerHolder;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.LineButton;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;

import java.util.ArrayList;
import java.util.List;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/**
 * EMCL contact / feedback page.
 *
 * <p>Provides the QQ group link, a feedback mail entry and the "report" dialog.
 * The report dialog does not actually send anything: it reminds the user that
 * server-side matters (underage top-ups, bans, item disputes, refunds, server
 * rules) belong to server owners / payment channels / Microsoft-Mojang support,
 * and that EMCL is distributed as-is under GPL-3.0.
 */
public class FeedbackPage extends SpinnerPane {

    private final WeakListenerHolder holder = new WeakListenerHolder();

    public FeedbackPage() {
        VBox content = new VBox();
        content.getStyleClass().add("spinner-pane-content");
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        FXUtils.smoothScrolling(scrollPane);
        setContent(scrollPane);

        ComponentList groups = new ComponentList();
        {
            var users = LineButton.createExternalLinkButton(Metadata.GROUPS_URL);
            users.setLargeTitle(true);
            users.setLeading(FXUtils.newBuiltinImage("/assets/img/icon.png"));
            users.setTitle(i18n("contact.chat.qq_group"));
            users.setSubtitle(i18n("contact.chat.qq_group.statement"));

            groups.getContent().setAll(users);
        }

        ComponentList feedback = new ComponentList();
        {
            var mail = LineButton.createExternalLinkButton("mailto:" + Metadata.FEEDBACK_MAIL);
            mail.setLargeTitle(true);
            mail.setLeading(FXUtils.newBuiltinImage("/assets/img/icon-title@2x.png"));
            mail.setTitle(i18n("contact.feedback.mail"));
            mail.setSubtitle(i18n("contact.feedback.mail.statement"));

            var report = new LineButton();
            report.setLargeTitle(true);
            report.setTitle(i18n("contact.report"));
            report.setSubtitle(i18n("contact.report.statement"));
            report.setOnAction(event -> showReportDialog());

            feedback.getContent().setAll(mail, report);
        }

        content.getChildren().addAll(
                ComponentList.createComponentListTitle(i18n("contact.chat")),
                groups,
                ComponentList.createComponentListTitle(i18n("contact.feedback")),
                feedback
        );
    }

    private void showReportDialog() {
        List<String> items = List.of(
                i18n("contact.report.item.underage_topup"),
                i18n("contact.report.item.server_ban"),
                i18n("contact.report.item.item_dispute"),
                i18n("contact.report.item.payment_refund"),
                i18n("contact.report.item.server_rules")
        );

        VBox body = new VBox(8);
        body.setAlignment(Pos.CENTER_LEFT);

        Label hint = new Label(i18n("contact.report.hint"));
        hint.setWrapText(true);
        body.getChildren().add(hint);

        List<CheckBox> boxes = new ArrayList<>();
        for (String item : items) {
            CheckBox box = new CheckBox(item);
            boxes.add(box);
            body.getChildren().add(box);
        }

        TextField other = new TextField();
        other.setPromptText(i18n("contact.report.other"));
        HBox otherRow = new HBox(8, new Label(i18n("contact.report.other_label")), other);
        HBox.setHgrow(other, Priority.ALWAYS);
        body.getChildren().add(otherRow);

        final int MAX_SELECTED = 5;
        for (CheckBox box : boxes) {
            box.selectedProperty().addListener((obs, oldVal, newVal) -> {
                if (Boolean.TRUE.equals(newVal)) {
                    long selected = boxes.stream().filter(CheckBox::isSelected).count();
                    if (selected > MAX_SELECTED) {
                        box.setSelected(false);
                    }
                }
            });
        }

        JFXButton ok = new JFXButton(i18n("button.ok"));
        ok.getStyleClass().add("dialog-accept");

        JFXButton cancel = new JFXButton(i18n("button.cancel"));
        cancel.getStyleClass().add("dialog-cancel");

        HBox buttons = new HBox(12, ok, cancel);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(12, body, buttons);
        root.setMinWidth(420);

        ok.setOnAction(e -> {
            DialogUtils.close(root);
            showReportDiscourageDialog();
        });
        cancel.setOnAction(e -> DialogUtils.close(root));

        Controllers.dialog(root);
    }

    private void showReportDiscourageDialog() {
        String text = i18n("contact.report.discourage");
        MessageDialogPane pane = new MessageDialogPane.Builder(text, i18n("contact.report.discourage_title"), MessageDialogPane.MessageType.WARNING).build();

        JFXButton understand = new JFXButton(i18n("contact.report.understand"));
        understand.getStyleClass().add("dialog-accept");
        understand.setOnAction(e -> DialogUtils.close(pane));
        pane.addButton(understand);

        Controllers.dialog(pane);
    }
}
