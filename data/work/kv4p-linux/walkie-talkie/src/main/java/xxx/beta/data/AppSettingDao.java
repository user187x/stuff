/*
kv4p HT (see http://kv4p.com)
Copyright (C) 2024 Vance Vagell

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package xxx.beta.data;


import java.util.List;

@Dao
public interface AppSettingDao {
    @Query("SELECT * FROM app_settings")
    List<com.vagell.kv4pht.data.AppSetting> getAll();

    @Query("SELECT * FROM app_settings WHERE `name` LIKE :name LIMIT 1")
    com.vagell.kv4pht.data.AppSetting getByName(String name);

    @Insert
    void insertAll(com.vagell.kv4pht.data.AppSetting... appSettings);

    @Delete
    void delete(com.vagell.kv4pht.data.AppSetting appSetting);

    @Update
    void update(com.vagell.kv4pht.data.AppSetting appSetting);
}
