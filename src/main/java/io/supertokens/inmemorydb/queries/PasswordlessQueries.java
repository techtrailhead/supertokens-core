/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
 *
 *    This software is licensed under the Apache License, Version 2.0 (the
 *    "License") as published by the Apache Software Foundation.
 *
 *    You may not use this file except in compliance with the License. You may
 *    obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 */

package io.supertokens.inmemorydb.queries;

import io.supertokens.inmemorydb.ConnectionPool;
import io.supertokens.inmemorydb.ConnectionWithLocks;
import io.supertokens.inmemorydb.Start;
import io.supertokens.inmemorydb.config.Config;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.RowMapper;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.passwordless.UserInfo;
import io.supertokens.pluginInterface.passwordless.PasswordlessCode;
import io.supertokens.pluginInterface.passwordless.PasswordlessDevice;

import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

public class PasswordlessQueries {
    public static String getQueryToCreateUsersTable(Start start) {
        return "CREATE TABLE IF NOT EXISTS " + Config.getConfig(start).getPasswordlessUsersTable() + " ("
                + "user_id CHAR(36) NOT NULL," + "email VARCHAR(256) UNIQUE," + "phone_number VARCHAR(256) UNIQUE,"
                + "time_joined BIGINT UNSIGNED NOT NULL," + "PRIMARY KEY (user_id));";
    }

    public static String getQueryToCreateDevicesTable(Start start) {
        return "CREATE TABLE IF NOT EXISTS " + Config.getConfig(start).getPasswordlessDevicesTable() + " ("
                + "device_id_hash CHAR(44) NOT NULL," + "email VARCHAR(256)," + "phone_number VARCHAR(256),"
                + "failed_attempts INT UNSIGNED NOT NULL," + "PRIMARY KEY (device_id_hash));";
    }

    public static String getQueryToCreateCodesTable(Start start) {
        return "CREATE TABLE IF NOT EXISTS " + Config.getConfig(start).getPasswordlessCodesTable() + " ("
                + "code_id CHAR(36) NOT NULL," + "device_id_hash CHAR(44) NOT NULL,"
                + "link_code_hash CHAR(44) NOT NULL UNIQUE," + "created_at BIGINT UNSIGNED NOT NULL,"
                + "PRIMARY KEY (code_id),"
                + "FOREIGN KEY (device_id_hash) REFERENCES passwordless_devices(device_id_hash) ON DELETE CASCADE ON UPDATE CASCADE);";
    }

    public static String getQueryToCreateDeviceEmailIndex(Start start) {
        return "CREATE INDEX passwordless_devices_email_index ON "
                + Config.getConfig(start).getPasswordlessDevicesTable() + " (email);"; // USING hash
    }

    public static String getQueryToCreateDevicePhoneNumberIndex(Start start) {
        return "CREATE INDEX passwordless_devices_phone_number_index ON "
                + Config.getConfig(start).getPasswordlessDevicesTable() + " (phone_number);"; // USING hash
    }

    public static String getQueryToCreateCodeCreatedAtIndex(Start start) {
        return "CREATE INDEX passwordless_codes_created_at_index ON "
                + Config.getConfig(start).getPasswordlessCodesTable() + "(created_at);";
    }

    public static void createDevice_Transaction(Start start, Connection con, String deviceIdHash, String email,
            String phoneNumber) throws SQLException {
        String QUERY = "INSERT INTO " + Config.getConfig(start).getPasswordlessDevicesTable()
                + "(device_id_hash, email, phone_number, failed_attempts)" + " VALUES(?, ?, ?, 0)";
        try (PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, deviceIdHash);
            pst.setString(2, email);
            pst.setString(3, phoneNumber);
            pst.executeUpdate();
        }
    }

    public static PasswordlessDevice getDevice_Transaction(Start start, Connection con, String deviceIdHash)
            throws StorageQueryException, SQLException {
        String QUERY = "SELECT device_id_hash, email, phone_number, failed_attempts FROM "
                + Config.getConfig(start).getPasswordlessDevicesTable() + " WHERE device_id_hash = ?";

        try (PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, deviceIdHash);
            ResultSet result = pst.executeQuery();
            if (result.isAfterLast()) {
                return null;
            }
            return PasswordlessDeviceRowMapper.getInstance().mapOrThrow(result);
        }
    }

    public static void incrementDeviceFailedAttemptCount_Transaction(Start start, Connection con, String deviceIdHash)
            throws SQLException {
        String QUERY = "UPDATE " + Config.getConfig(start).getPasswordlessDevicesTable()
                + " SET failed_attempts = failed_attempts + 1 WHERE device_id_hash = ?";

        try (PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, deviceIdHash);
            pst.executeUpdate();
        }
    }

    public static void deleteDevice_Transaction(Start start, Connection con, String deviceIdHash) throws SQLException {
        String QUERY = "DELETE FROM " + Config.getConfig(start).getPasswordlessDevicesTable()
                + " WHERE device_id_hash = ?";

        try (PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, deviceIdHash);
            pst.executeUpdate();
        }
    }

    public static void deleteDevicesByPhoneNumber_Transaction(Start start, Connection con, @Nonnull String phoneNumber)
            throws SQLException {
        String QUERY = "DELETE FROM " + Config.getConfig(start).getPasswordlessDevicesTable()
                + " WHERE phone_number = ?";

        try (PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, phoneNumber);
            pst.executeUpdate();
        }
    }

    public static void deleteDevicesByEmail_Transaction(Start start, Connection con, @Nonnull String email)
            throws SQLException {
        String QUERY = "DELETE FROM " + Config.getConfig(start).getPasswordlessDevicesTable() + " WHERE email = ?";

        try (PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, email);
            pst.executeUpdate();
        }
    }

    public static void createCode_Transaction(Start start, Connection con, String codeId, String deviceIdHash,
            String linkCodeHash, long createdAt) throws SQLException {
        String QUERY = "INSERT INTO " + Config.getConfig(start).getPasswordlessCodesTable()
                + "(code_id, device_id_hash, link_code_hash, created_at)" + " VALUES(?, ?, ?, ?)";
        try (PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, codeId);
            pst.setString(2, deviceIdHash);
            pst.setString(3, linkCodeHash);
            pst.setLong(4, createdAt);
            pst.executeUpdate();
        }
    }

    public static PasswordlessCode[] getCodesOfDevice_Transaction(Start start, Connection con, String deviceIdHash)
            throws StorageQueryException, SQLException {
        String QUERY = "SELECT code_id, device_id_hash, link_code_hash, created_at FROM "
                + Config.getConfig(start).getPasswordlessCodesTable() + " WHERE device_id_hash = ?";

        try (PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, deviceIdHash);
            ResultSet result = pst.executeQuery();
            List<PasswordlessCode> temp = new ArrayList<>();
            while (result.next()) {
                temp.add(PasswordlessCodeRowMapper.getInstance().mapOrThrow(result));
            }
            PasswordlessCode[] finalResult = new PasswordlessCode[temp.size()];
            for (int i = 0; i < temp.size(); i++) {
                finalResult[i] = temp.get(i);
            }
            return finalResult;
        }
    }

    public static PasswordlessCode getCodeByLinkCodeHash_Transaction(Start start, Connection con, String linkCodeHash)
            throws StorageQueryException, SQLException {
        String QUERY = "SELECT code_id, device_id_hash, link_code_hash, created_at FROM "
                + Config.getConfig(start).getPasswordlessCodesTable() + " WHERE link_code_hash = ?";

        try (PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, linkCodeHash);
            ResultSet result = pst.executeQuery();
            if (result.isAfterLast()) {
                return null;
            }
            return PasswordlessCodeRowMapper.getInstance().mapOrThrow(result);
        }
    }

    public static void deleteCode_Transaction(Start start, Connection con, String codeId) throws SQLException {
        String QUERY = "DELETE FROM " + Config.getConfig(start).getPasswordlessCodesTable() + " WHERE code_id = ?";

        try (PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, codeId);
            pst.executeUpdate();
        }
    }

    public static void deleteCodesOfDeviceBefore_Transaction(Start start, Connection con, String deviceIdHash,
            long time) throws SQLException {
        String QUERY = "DELETE FROM " + Config.getConfig(start).getPasswordlessCodesTable()
                + " WHERE device_id_hash = ? AND created_at < ?";
        try (PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, deviceIdHash);
            pst.setLong(2, time);
            pst.executeUpdate();
        }
    }

    public static void createUser_Transaction(Start start, Connection con, String userId, String email,
            String phoneNumber, long timeJoined) throws SQLException {
        {
            String QUERY = "INSERT INTO " + Config.getConfig(start).getUsersTable()
                    + "(user_id, recipe_id, time_joined)" + " VALUES(?, ?, ?)";
            try (PreparedStatement pst = con.prepareStatement(QUERY)) {
                pst.setString(1, userId);
                pst.setString(2, RECIPE_ID.PASSWORDLESS.toString());
                pst.setLong(3, timeJoined);
                pst.executeUpdate();
            }
        }

        {
            String QUERY = "INSERT INTO " + Config.getConfig(start).getPasswordlessUsersTable()
                    + "(user_id, email, phone_number, time_joined)" + " VALUES(?, ?, ?)";
            try (PreparedStatement pst = con.prepareStatement(QUERY)) {
                pst.setString(1, userId);
                pst.setString(2, email);
                pst.setString(3, phoneNumber);
                pst.setLong(4, timeJoined);
                pst.executeUpdate();
            }
        }
    }

    public static void updateUser_Transaction(Start start, Connection con, String userId, String email,
            String phoneNumber) throws SQLException {
        String QUERY = "UPDATE " + Config.getConfig(start).getPasswordlessUsersTable()
                + " SET email = ?, phone_number = ? WHERE user_id = ?";

        try (PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, email);
            pst.setString(2, phoneNumber);
            pst.setString(3, userId);
            pst.executeUpdate();
        }
    }

    public static PasswordlessDevice getDevice(Start start, String deviceIdHash)
            throws StorageQueryException, SQLException {
        try (Connection con = ConnectionPool.getConnection(start)) {
            return getDevice_Transaction(start, con, deviceIdHash);
        }
    }

    public static PasswordlessDevice[] getDevicesByEmail(Start start, @Nonnull String email)
            throws StorageQueryException, SQLException {
        String QUERY = "SELECT device_id_hash, email, phone_number, failed_attempts FROM "
                + Config.getConfig(start).getPasswordlessDevicesTable() + " WHERE email = ?";

        try (Connection con = ConnectionPool.getConnection(start);
                PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, email);
            ResultSet result = pst.executeQuery();
            List<PasswordlessDevice> temp = new ArrayList<>();
            while (result.next()) {
                temp.add(PasswordlessDeviceRowMapper.getInstance().mapOrThrow(result));
            }
            PasswordlessDevice[] finalResult = new PasswordlessDevice[temp.size()];
            for (int i = 0; i < temp.size(); i++) {
                finalResult[i] = temp.get(i);
            }
            return finalResult;
        }
    }

    public static PasswordlessDevice[] getDevicesByPhoneNumber(Start start, @Nonnull String phoneNumber)
            throws StorageQueryException, SQLException {
        String QUERY = "SELECT device_id_hash, email, phone_number, failed_attempts FROM "
                + Config.getConfig(start).getPasswordlessDevicesTable() + " WHERE phone_number = ?";

        try (Connection con = ConnectionPool.getConnection(start);
                PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, phoneNumber);
            ResultSet result = pst.executeQuery();
            List<PasswordlessDevice> temp = new ArrayList<>();
            while (result.next()) {
                temp.add(PasswordlessDeviceRowMapper.getInstance().mapOrThrow(result));
            }
            PasswordlessDevice[] finalResult = new PasswordlessDevice[temp.size()];
            for (int i = 0; i < temp.size(); i++) {
                finalResult[i] = temp.get(i);
            }
            return finalResult;
        }
    }

    public static PasswordlessCode[] getCodesOfDevice(Start start, String deviceIdHash)
            throws StorageQueryException, SQLException {
        try (Connection con = ConnectionPool.getConnection(start)) {
            return PasswordlessQueries.getCodesOfDevice_Transaction(start, con, deviceIdHash);
        }
    }

    public static PasswordlessCode[] getCodesBefore(Start start, long time) throws StorageQueryException, SQLException {
        String QUERY = "SELECT code_id, device_id_hash, link_code_hash, created_at FROM "
                + Config.getConfig(start).getPasswordlessCodesTable() + " WHERE created_at < ?";

        try (Connection con = ConnectionPool.getConnection(start);
                PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setLong(1, time);
            ResultSet result = pst.executeQuery();
            List<PasswordlessCode> temp = new ArrayList<>();
            while (result.next()) {
                temp.add(PasswordlessCodeRowMapper.getInstance().mapOrThrow(result));
            }
            PasswordlessCode[] finalResult = new PasswordlessCode[temp.size()];
            for (int i = 0; i < temp.size(); i++) {
                finalResult[i] = temp.get(i);
            }
            return finalResult;
        }
    }

    public static PasswordlessCode getCode(Start start, String codeId) throws StorageQueryException, SQLException {
        String QUERY = "SELECT code_id, device_id_hash, link_code_hash, created_at FROM "
                + Config.getConfig(start).getPasswordlessCodesTable() + " WHERE code_id = ?";

        try (Connection con = ConnectionPool.getConnection(start);
                PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, codeId);
            ResultSet result = pst.executeQuery();
            if (result.isAfterLast()) {
                return null;
            }
            return PasswordlessCodeRowMapper.getInstance().mapOrThrow(result);
        }
    }

    public static PasswordlessCode getCodeByLinkCodeHash(Start start, String linkCodeHash)
            throws StorageQueryException, SQLException {
        try (Connection con = ConnectionPool.getConnection(start)) {
            return PasswordlessQueries.getCodeByLinkCodeHash_Transaction(start, con, linkCodeHash);
        }
    }

    public static UserInfo getUserById(Start start, String userId) throws StorageQueryException, SQLException {
        String QUERY = "SELECT user_id, email, phone_number, time_joined FROM "
                + Config.getConfig(start).getPasswordlessUsersTable() + " WHERE user_id = ?";

        try (Connection con = ConnectionPool.getConnection(start);
                PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, userId);
            ResultSet result = pst.executeQuery();

            return UserInfoRowMapper.getInstance().mapOrThrow(result);
        }
    }

    public static UserInfo getUserByEmail(Start start, @Nonnull String email)
            throws StorageQueryException, SQLException {
        String QUERY = "SELECT user_id, email, phone_number, time_joined FROM "
                + Config.getConfig(start).getPasswordlessUsersTable() + " WHERE email = ?";

        try (Connection con = ConnectionPool.getConnection(start);
                PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, email);
            ResultSet result = pst.executeQuery();

            return UserInfoRowMapper.getInstance().mapOrThrow(result);
        }
    }

    public static UserInfo getUserByPhoneNumber(Start start, @Nonnull String phoneNumber)
            throws StorageQueryException, SQLException {
        String QUERY = "SELECT user_id, email, phone_number, time_joined FROM "
                + Config.getConfig(start).getPasswordlessUsersTable() + " WHERE phone_number = ?";

        try (Connection con = ConnectionPool.getConnection(start);
                PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, phoneNumber);
            ResultSet result = pst.executeQuery();

            return UserInfoRowMapper.getInstance().mapOrThrow(result);
        }
    }

    private static class PasswordlessDeviceRowMapper implements RowMapper<PasswordlessDevice, ResultSet> {
        private static final PasswordlessDeviceRowMapper INSTANCE = new PasswordlessDeviceRowMapper();

        private PasswordlessDeviceRowMapper() {
        }

        private static PasswordlessDeviceRowMapper getInstance() {
            return INSTANCE;
        }

        @Override
        public PasswordlessDevice map(ResultSet result) throws Exception {
            return new PasswordlessDevice(result.getString("device_id_hash"), result.getString("email"),
                    result.getString("phone_number"), result.getInt("failed_attempts"));
        }
    }

    private static class PasswordlessCodeRowMapper implements RowMapper<PasswordlessCode, ResultSet> {
        private static final PasswordlessCodeRowMapper INSTANCE = new PasswordlessCodeRowMapper();

        private PasswordlessCodeRowMapper() {
        }

        private static PasswordlessCodeRowMapper getInstance() {
            return INSTANCE;
        }

        @Override
        public PasswordlessCode map(ResultSet result) throws Exception {
            return new PasswordlessCode(result.getString("code_id"), result.getString("device_id_hash"),
                    result.getString("link_code_hash"), result.getLong("created_at"));
        }
    }

    private static class UserInfoRowMapper implements RowMapper<UserInfo, ResultSet> {
        private static final UserInfoRowMapper INSTANCE = new UserInfoRowMapper();

        private UserInfoRowMapper() {
        }

        private static UserInfoRowMapper getInstance() {
            return INSTANCE;
        }

        @Override
        public UserInfo map(ResultSet result) throws Exception {
            return new UserInfo(result.getString("user_id"), result.getString("email"),
                    result.getString("phone_number"), result.getLong("time_joined"));
        }
    }
}
