Name:           jpms-invalid
Version:        1
Release:        1
Summary:        RPM that contains a JAR with invalid module-info.class
License:        CC0
BuildArch:      noarch
BuildRequires:  javapackages-local
BuildRequires:  zip

%description
%{summary}.

%prep
%setup -cT

%build
echo 'class foo{}' >foo.java
javac --release 21 foo.java
dd if=foo.class of=module-info.class bs=42 count=1
echo XAXAXA >>module-info.class
touch mf
jar cmf mf out.jar
zip -u out.jar module-info.class
%mvn_artifact jpms:invalid:1 out.jar

%install
%mvn_install

%files -f .mfiles

%changelog
