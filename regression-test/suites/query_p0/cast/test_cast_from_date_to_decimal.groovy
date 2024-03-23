// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

suite("test_cast_from_date_to_decimal") {
    qt_sql """select cast(a as decimal) from (select cast("2000-01-01" as date) a) t1;"""

    qt_sql """select cast(b as decimal) from (select cast("1000-01-01" as date) b) t2;"""

    qt_sql """select cast(c as decimal) from (select cast("9999-12-31" as date) c) t3;"""
} 