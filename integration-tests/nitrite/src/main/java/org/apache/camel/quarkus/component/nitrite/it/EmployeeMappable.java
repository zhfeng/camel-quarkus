/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.quarkus.component.nitrite.it;

import java.util.Date;

import org.dizitart.no2.Document;
import org.dizitart.no2.IndexType;
import org.dizitart.no2.mapper.Mappable;
import org.dizitart.no2.mapper.NitriteMapper;
import org.dizitart.no2.objects.Id;
import org.dizitart.no2.objects.Index;
import org.dizitart.no2.objects.Indices;

@Indices({
        @Index(value = "address", type = IndexType.NonUnique),
        @Index(value = "name", type = IndexType.Unique)
})
public class EmployeeMappable extends Employee implements Mappable {

    @Id
    private long empId;

    public EmployeeMappable() {
    }

    public EmployeeMappable(long empId, Date joinDate, String name, String address) {
        super(empId, joinDate, name, address);
    }

    public EmployeeMappable(EmployeeSerializable employee) {
        super(employee.getEmpId(), employee.getJoinDate(), employee.getName(), employee.getAddress());
    }

    @Override
    public long getEmpId() {
        return empId;
    }

    @Override
    public void setEmpId(long empId) {
        this.empId = empId;
    }

    @Override
    public Document write(NitriteMapper nitriteMapper) {
        Document document = new Document();
        document.put("empId", getEmpId());
        document.put("name", getName());
        document.put("joiningDate", getJoinDate());
        document.put("address", getAddress());

        return document;
    }

    @Override
    public void read(NitriteMapper nitriteMapper, Document document) {
        if (document != null) {
            setEmpId((Long) document.get("empId"));
            setName((String) document.get("name"));
            setJoinDate((Date) document.get("joiningDate"));
            setAddress((String) document.get("address"));
        }
    }
}
