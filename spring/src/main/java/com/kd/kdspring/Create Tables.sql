CREATE TABLE users(  
    user_id int NOT NULL primary key AUTO_INCREMENT comment 'primary key',
    username varchar(255) comment 'user name',
    password varchar(255) comment 'password',
    roles varchar(255) comment 'roles'
) default charset utf8 comment '';