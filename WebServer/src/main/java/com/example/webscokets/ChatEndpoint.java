package com.example.webscokets;

import com.example.webscokets.dbRunTime.Group;
import com.example.webscokets.dbRunTime.Groups;
import com.example.webscokets.dbRunTime.User;
import com.example.webscokets.dbRunTime.Users;
import com.example.webscokets.model.Message;
import com.example.webscokets.model.MessageDecoder;
import com.example.webscokets.model.MessageEncoder;
import com.example.webscokets.model.MessageType;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Optional;

@ServerEndpoint(value = "/chat/{username}", decoders = MessageDecoder.class, encoders = MessageEncoder.class)
public class ChatEndpoint {
    private Session session;

    @OnOpen
    public void onOpen(Session session, @PathParam("username") String username) {
        this.session = session;
        Optional<User> existingUserOptional = Users.INSTANCE.users
                .stream()
                .filter(user -> user.getName().equals(username))
                .findFirst();

        if (existingUserOptional.isPresent()) {
            // If the user already exists, update the session
            User existingUser = existingUserOptional.get();
            existingUser.setSession(session);
        } else {
            // If the user does not exist, create a new user
            User newUser = new User();
            newUser.setName(username);
            newUser.setSession(session);
            Users.INSTANCE.users.add(newUser);
        }

        refreshUsers();
        refreshGroups();
    }


    public void refreshUsers() {
        for (User user : Users.INSTANCE.users) {

            String users = Users.INSTANCE.users
                    .stream()
                    .map(User::getName)
                    .reduce((s, s2) -> s + ", " + s2)
                    .get();

            Message allUsersMessage = new Message();
            allUsersMessage.setContent(users);
            allUsersMessage.setType(MessageType.VIEW_ALL_USERS);
            /* TODO: Return "Mihai, Ioana, Alina, ..."*/
            this.sendMessage(user.getSession(), allUsersMessage);

        }
    }

    public void refreshGroups() {
        for (User user : Users.INSTANCE.users) {

            String groups = Groups.INSTANCE.groups
                    .stream()
                    .map(Group::getName)
                    .reduce((s, s2) -> s + ", " + s2)
                    .orElse("");

            Message allGroupsMessage = new Message();
            allGroupsMessage.setContent(groups);
            allGroupsMessage.setType(MessageType.VIEW_ALL_GROUPS);

            this.sendMessage(user.getSession(), allGroupsMessage);

        }
    }

    @OnMessage
    public void onMessage(Session session, Message message) {
        switch (message.getType()) {
            case CREATE_GROUP: {
                Group group = new Group();
                group.setName(message.getContent());
                Groups.INSTANCE.groups.add(group);
                addUserToGroup(Optional.ofNullable(group), message.getFrom());
                refreshGroups();
            }
            break;
            case MESSAGE_TO_GROUP: {
                String groupName = message.getTo();
                Optional<Group> groupOptional = Groups.INSTANCE.groups
                        .stream()
                        .filter(group -> group.getName().equals(groupName))
                        .findFirst();

                if (groupOptional.isPresent()) {
                    Group group = groupOptional.get();

                    boolean senderInGroup = group.getUsers()
                            .stream()
                            .anyMatch(user -> user.getName().equals(message.getFrom()));

                    if (senderInGroup) {
                        // Iterate through the users in the group and send the message to each user
                        for (User user : group.getUsers()) {
                            // Create a new message for each user in the group
                            Message groupMessage = new Message();
                            groupMessage.setType(MessageType.MESSAGE_TO_GROUP);
                            groupMessage.setFrom(message.getFrom()); // Set the sender
                            groupMessage.setTo(groupName);
                            groupMessage.setContent(message.getContent()); // Set the message content

                            // Send the message to the user
                            sendMessage(user.getSession(), groupMessage);
                        }
                    }
                }
            }
            break;
            case PRIVATE_MESSAGE: {
                String recipientName = message.getTo();
                String senderName = message.getFrom();
                String content = message.getContent();

                Optional<User> recipientOptional = Users.INSTANCE.users
                        .stream()
                        .filter(user -> user.getName().equals(recipientName))
                        .findFirst();

                Optional<User> senderOptional = Users.INSTANCE.users
                        .stream()
                        .filter(user -> user.getName().equals(senderName))
                        .findFirst();

                if (recipientOptional.isPresent()) {
                    User recipient = recipientOptional.get();
                    User sender = senderOptional.get();

                    // Create a new message for the recipient
                    Message privateMessage = new Message();
                    privateMessage.setType(MessageType.PRIVATE_MESSAGE);
                    privateMessage.setFrom(senderName);
                    privateMessage.setContent(content);

                    // Send the private message to the recipient
                    sendMessage(recipient.getSession(), privateMessage);
                    sendMessage(sender.getSession(), privateMessage);
                }
            }
            break;
            case ADD_TO_GROUP: {
                /* Message.to: group
                 *  Message.content: username */
                String groupName = message.getTo();
                Optional<Group> firstGroup = Groups.INSTANCE.groups
                        .stream()
                        .filter(group -> group.getName().equals(groupName))
                        .findFirst();

                addUserToGroup(firstGroup, message.getContent());
            }
            break;
            case REMOVE_FROM_GROUP: {
                String groupName = message.getTo();
                String userNameToRemove = message.getContent();

                Optional<Group> groupOptional = Groups.INSTANCE.groups
                        .stream()
                        .filter(group -> group.getName().equals(groupName))
                        .findFirst();

                if (groupOptional.isPresent()) {
                    Group group = groupOptional.get();
                    group.getUsers().removeIf(user -> user.getName().equals(userNameToRemove));
                }
            }
            break;
        }
    }

    public void addUserToGroup(Optional<Group> firstGroup, String username)
    {
        Optional<User> firstUser = Users.INSTANCE.users
                .stream()
                .filter(user -> user.getName().equals(username))
                .findFirst();

        if (firstGroup.isPresent() && firstUser.isPresent()) {
            firstGroup.get().getUsers().add(firstUser.get());
        }

    }

    @OnClose
    public void onClose(Session session) {

    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        System.out.println("Error " + throwable);
    }

    private void sendMessage(Message message) {
        try {
            session.getBasicRemote().sendObject(message);
        } catch (IOException | EncodeException e) {
            throw new RuntimeException(e);
        }
    }

    private void sendMessage(Session session, Message message) {
        try {
            session.getBasicRemote().sendObject(message);
        } catch (IOException | EncodeException e) {
            throw new RuntimeException(e);
        }
    }
}